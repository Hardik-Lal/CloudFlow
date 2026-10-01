package com.cloudflow.deployment.service;

import com.cloudflow.audit.domain.AuditAction;
import com.cloudflow.audit.service.AuditLogger;
import com.cloudflow.common.exception.ConflictException;
import com.cloudflow.common.exception.ResourceNotFoundException;
import com.cloudflow.common.exception.UnprocessableException;
import com.cloudflow.common.exception.UnprocessableException.FieldIssue;
import com.cloudflow.common.web.PageResponse;
import com.cloudflow.deployment.domain.Deployment;
import com.cloudflow.deployment.domain.DeploymentStatus;
import com.cloudflow.deployment.domain.TriggerType;
import com.cloudflow.deployment.dto.DeploymentLogResponse;
import com.cloudflow.deployment.dto.DeploymentResponse;
import com.cloudflow.deployment.engine.DeploymentEndpoints;
import com.cloudflow.deployment.repository.DeploymentLogRepository;
import com.cloudflow.deployment.repository.DeploymentRepository;
import com.cloudflow.environment.dto.EnvironmentRef;
import com.cloudflow.environment.dto.ValidationResult;
import com.cloudflow.environment.service.DeploymentConfigService;
import com.cloudflow.environment.service.EnvironmentAccessService;
import com.cloudflow.organization.domain.Permission;
import com.cloudflow.project.service.ProjectAccessService;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeploymentService {

  private final AuditLogger audit;

  static final int MAX_LOG_LINES_PER_REQUEST = 1000;

  private final DeploymentRepository deploymentRepository;
  private final DeploymentLogRepository logRepository;
  private final EnvironmentAccessService environmentAccess;
  private final ProjectAccessService projectAccess;
  private final DeploymentConfigService configService;
  private final ApplicationEventPublisher events;
  private final DeploymentEndpoints endpoints;
  private final DeploymentStateService stateService;
  private final Clock clock;

  public DeploymentService(
      DeploymentRepository deploymentRepository,
      DeploymentLogRepository logRepository,
      EnvironmentAccessService environmentAccess,
      ProjectAccessService projectAccess,
      DeploymentConfigService configService,
      ApplicationEventPublisher events,
      DeploymentEndpoints endpoints,
      DeploymentStateService stateService,
      Clock clock,
      AuditLogger audit) {
    this.audit = audit;
    this.deploymentRepository = deploymentRepository;
    this.logRepository = logRepository;
    this.environmentAccess = environmentAccess;
    this.projectAccess = projectAccess;
    this.configService = configService;
    this.events = events;
    this.endpoints = endpoints;
    this.stateService = stateService;
    this.clock = clock;
  }

  /** Queues a deployment of the environment's branch (or a specific commit). */
  @Transactional
  public DeploymentResponse trigger(UUID environmentId, UUID userId, String commitSha) {
    EnvironmentRef environment = requireTrigger(environmentId, userId);
    return queue(environment, userId, TriggerType.MANUAL, commitSha, null);
  }

  /**
   * Queues a deployment on behalf of a CI pipeline. Internal: the caller has authenticated the
   * pipeline and chosen the user whose GitHub access is used.
   */
  @Transactional
  public DeploymentResponse triggerFromPipeline(
      EnvironmentRef environment, UUID actingUserId, String commitSha, Long githubRunId) {
    return queue(environment, actingUserId, TriggerType.PIPELINE, commitSha, githubRunId);
  }

  /** Redeploys the image of an earlier successful deployment. */
  @Transactional
  public DeploymentResponse rollback(UUID deploymentId, UUID userId) {
    Deployment target = load(deploymentId);
    EnvironmentRef environment = requireTrigger(target.getEnvironmentId(), userId);
    if (target.getImageTag() == null
        || (target.getStatus() != DeploymentStatus.SUCCEEDED
            && target.getStatus() != DeploymentStatus.ROLLED_BACK)) {
      throw new ConflictException("Only successful deployments can be rolled back to");
    }
    if (target.isActive()) {
      throw new ConflictException("This deployment is already serving the environment");
    }
    ensureNothingInProgress(target.getEnvironmentId());
    Deployment rollback = deploymentRepository.save(Deployment.rollbackTo(target, userId));
    audit.record(
        environment.organizationId(),
        userId,
        AuditAction.DEPLOYMENT_ROLLBACK_TRIGGERED,
        "deployment",
        rollback.getId(),
        Map.of(
            "environment", environment.type().name(),
            "rollbackOf", target.getId().toString(),
            "imageTag", target.getImageTag()));
    events.publishEvent(new DeploymentQueuedEvent(rollback.getId()));
    stateService.publish(rollback);
    return DeploymentResponse.from(rollback, endpoints.publicHost(rollback.getTarget()));
  }

  /** Cancels a deployment that has not started its container yet. */
  @Transactional
  public DeploymentResponse cancel(UUID deploymentId, UUID userId) {
    Deployment deployment = load(deploymentId);
    EnvironmentRef environment = requireTrigger(deployment.getEnvironmentId(), userId);
    if (!deployment.getStatus().isCancellable()) {
      throw new ConflictException(
          "Only queued or building deployments can be cancelled (status "
              + deployment.getStatus()
              + ")");
    }
    deployment.cancel(clock.instant());
    audit.record(
        environment.organizationId(),
        userId,
        AuditAction.DEPLOYMENT_CANCELLED,
        "deployment",
        deploymentId,
        Map.of("environment", environment.type().name()));
    stateService.publish(deployment);
    return DeploymentResponse.from(deployment, endpoints.publicHost(deployment.getTarget()));
  }

  @Transactional(readOnly = true)
  public DeploymentResponse get(UUID deploymentId, UUID userId) {
    Deployment deployment = load(deploymentId);
    requireRead(deployment, userId);
    return DeploymentResponse.from(deployment, endpoints.publicHost(deployment.getTarget()));
  }

  /**
   * Checks that the user may read the deployment.
   *
   * @return the deployment's environment id
   */
  @Transactional(readOnly = true)
  public UUID authorizeRead(UUID deploymentId, UUID userId) {
    Deployment deployment = load(deploymentId);
    requireRead(deployment, userId);
    return deployment.getEnvironmentId();
  }

  /** Deployment details without a user check; the caller must have authorized access. */
  @Transactional(readOnly = true)
  public DeploymentResponse getInternal(UUID deploymentId) {
    Deployment deployment = load(deploymentId);
    return DeploymentResponse.from(deployment, endpoints.publicHost(deployment.getTarget()));
  }

  @Transactional(readOnly = true)
  public PageResponse<DeploymentResponse> listForEnvironment(
      UUID environmentId, UUID userId, Pageable pageable) {
    environmentAccess.require(
        environmentId, userId, Permission.DEPLOYMENT_READ, Permission.DEPLOYMENT_READ);
    return PageResponse.of(
        deploymentRepository.findAllByEnvironmentId(environmentId, pageable),
        deployment ->
            DeploymentResponse.from(deployment, endpoints.publicHost(deployment.getTarget())));
  }

  @Transactional(readOnly = true)
  public PageResponse<DeploymentResponse> listForProject(
      UUID projectId, UUID userId, Pageable pageable) {
    projectAccess.requirePermission(projectId, userId, Permission.DEPLOYMENT_READ);
    return PageResponse.of(
        deploymentRepository.findAllByProjectId(projectId, pageable),
        deployment ->
            DeploymentResponse.from(deployment, endpoints.publicHost(deployment.getTarget())));
  }

  /** Log lines after {@code afterId} (exclusive), oldest first. */
  @Transactional(readOnly = true)
  public List<DeploymentLogResponse> logs(UUID deploymentId, UUID userId, long afterId, int limit) {
    requireRead(load(deploymentId), userId);
    return logRepository
        .findAllByDeploymentIdAndIdGreaterThanOrderByIdAsc(
            deploymentId, afterId, Limit.of(Math.clamp(limit, 1, MAX_LOG_LINES_PER_REQUEST)))
        .stream()
        .map(DeploymentLogResponse::from)
        .toList();
  }

  private DeploymentResponse queue(
      EnvironmentRef environment,
      UUID userId,
      TriggerType triggerType,
      String commitSha,
      Long githubRunId) {
    ValidationResult validation = configService.validateForDeployment(environment.id());
    if (!validation.valid()) {
      throw new UnprocessableException(
          "The environment's deployment configuration is invalid",
          validation.errors().stream()
              .map(issue -> new FieldIssue(issue.field(), issue.message()))
              .toList());
    }
    ensureNothingInProgress(environment.id());
    Deployment deployment =
        deploymentRepository.save(
            Deployment.build(
                environment.projectId(),
                environment.id(),
                userId,
                triggerType,
                environment.branch(),
                commitSha));
    deployment.linkPipelineRun(githubRunId);
    Map<String, String> details = new LinkedHashMap<>();
    details.put("environment", environment.type().name());
    details.put("trigger", triggerType.name());
    details.put("commitSha", commitSha == null ? "branch head" : commitSha);
    if (githubRunId != null) {
      details.put("githubRunId", githubRunId.toString());
    }
    audit.record(
        environment.organizationId(),
        userId,
        AuditAction.DEPLOYMENT_TRIGGERED,
        "deployment",
        deployment.getId(),
        details);
    events.publishEvent(new DeploymentQueuedEvent(deployment.getId()));
    stateService.publish(deployment);
    return DeploymentResponse.from(deployment, endpoints.publicHost(deployment.getTarget()));
  }

  private void ensureNothingInProgress(UUID environmentId) {
    if (deploymentRepository.existsByEnvironmentIdAndStatusIn(
        environmentId, DeploymentStatus.IN_PROGRESS)) {
      throw new ConflictException("Another deployment of this environment is in progress");
    }
  }

  private EnvironmentRef requireTrigger(UUID environmentId, UUID userId) {
    return environmentAccess.require(
        environmentId,
        userId,
        Permission.DEPLOYMENT_TRIGGER,
        Permission.DEPLOYMENT_TRIGGER_PRODUCTION);
  }

  private void requireRead(Deployment deployment, UUID userId) {
    try {
      environmentAccess.require(
          deployment.getEnvironmentId(),
          userId,
          Permission.DEPLOYMENT_READ,
          Permission.DEPLOYMENT_READ);
    } catch (ResourceNotFoundException e) {
      throw new ResourceNotFoundException("Deployment", deployment.getId());
    }
  }

  private Deployment load(UUID deploymentId) {
    return deploymentRepository
        .findById(deploymentId)
        .orElseThrow(() -> new ResourceNotFoundException("Deployment", deploymentId));
  }
}
