package com.cloudflow.environment.service;

import com.cloudflow.audit.domain.AuditAction;
import com.cloudflow.audit.service.AuditLogger;
import com.cloudflow.common.exception.ConflictException;
import com.cloudflow.common.exception.InvalidRequestException;
import com.cloudflow.common.exception.ResourceNotFoundException;
import com.cloudflow.environment.domain.ConfigTemplate;
import com.cloudflow.environment.domain.DeploymentConfig;
import com.cloudflow.environment.domain.DeploymentSettings;
import com.cloudflow.environment.domain.Environment;
import com.cloudflow.environment.dto.CreateEnvironmentRequest;
import com.cloudflow.environment.dto.DeploymentConfigResponse;
import com.cloudflow.environment.dto.EnvironmentRef;
import com.cloudflow.environment.dto.EnvironmentResponse;
import com.cloudflow.environment.dto.UpdateEnvironmentRequest;
import com.cloudflow.environment.repository.DeploymentConfigRepository;
import com.cloudflow.environment.repository.EnvironmentRepository;
import com.cloudflow.environment.repository.EnvironmentVariableRepository;
import com.cloudflow.environment.repository.EnvironmentVariableRepository.VariableCounts;
import com.cloudflow.organization.domain.Permission;
import com.cloudflow.project.dto.ProjectSnapshot;
import com.cloudflow.project.service.ProjectAccessService;
import com.cloudflow.project.service.ProjectLookupService;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnvironmentService {

  private final AuditLogger audit;

  private final EnvironmentRepository environmentRepository;
  private final DeploymentConfigRepository configRepository;
  private final EnvironmentVariableRepository variableRepository;
  private final EnvironmentAccessService environmentAccessService;
  private final ProjectAccessService projectAccessService;
  private final ProjectLookupService projectLookupService;

  public EnvironmentService(
      EnvironmentRepository environmentRepository,
      DeploymentConfigRepository configRepository,
      EnvironmentVariableRepository variableRepository,
      EnvironmentAccessService environmentAccessService,
      ProjectAccessService projectAccessService,
      ProjectLookupService projectLookupService,
      AuditLogger audit) {
    this.audit = audit;
    this.environmentRepository = environmentRepository;
    this.configRepository = configRepository;
    this.variableRepository = variableRepository;
    this.environmentAccessService = environmentAccessService;
    this.projectAccessService = projectAccessService;
    this.projectLookupService = projectLookupService;
  }

  @Transactional(readOnly = true)
  public List<EnvironmentResponse> list(UUID projectId, UUID userId) {
    projectAccessService.requirePermission(projectId, userId, Permission.ENV_READ);
    List<Environment> environments =
        environmentRepository.findAllByProjectId(projectId).stream()
            .sorted(Comparator.comparing(Environment::getType))
            .toList();
    return toResponses(environments);
  }

  /** Creates an environment with the deployment template recommended for the project. */
  @Transactional
  public EnvironmentResponse create(UUID projectId, UUID userId, CreateEnvironmentRequest request) {
    UUID organizationId =
        projectAccessService
            .requirePermission(
                projectId,
                userId,
                EnvironmentAccessService.permissionFor(
                    request.type(), Permission.ENV_WRITE, Permission.ENV_WRITE_PRODUCTION))
            .organizationId();
    ProjectSnapshot project = projectLookupService.snapshot(projectId);
    String branch =
        request.branch() == null || request.branch().isBlank()
            ? project.defaultBranch()
            : request.branch().strip();
    requireKnownBranch(project, branch);
    if (environmentRepository.existsByProjectIdAndType(projectId, request.type())) {
      throw new ConflictException("The project already has a " + request.type() + " environment");
    }

    Environment environment =
        environmentRepository.save(new Environment(projectId, request.type(), branch));
    ConfigTemplate template = ConfigTemplate.recommendedFor(project.appType());
    configRepository.save(
        new DeploymentConfig(
            environment.getId(), DeploymentSettings.defaults(template, project.appType())));
    audit.record(
        organizationId,
        userId,
        AuditAction.ENVIRONMENT_CREATED,
        "environment",
        environment.getId(),
        Map.of("type", request.type().name(), "branch", branch, "projectId", projectId.toString()));
    return toResponses(List.of(environment)).getFirst();
  }

  @Transactional(readOnly = true)
  public EnvironmentResponse get(UUID environmentId, UUID userId) {
    environmentAccessService.requireRead(environmentId, userId);
    return toResponses(List.of(load(environmentId))).getFirst();
  }

  @Transactional
  public EnvironmentResponse update(
      UUID environmentId, UUID userId, UpdateEnvironmentRequest request) {
    EnvironmentRef ref = environmentAccessService.requireWrite(environmentId, userId);
    String branch = request.branch().strip();
    requireKnownBranch(projectLookupService.snapshot(ref.projectId()), branch);
    Environment environment = load(environmentId);
    String previousBranch = environment.getBranch();
    environment.changeBranch(branch);
    audit.record(
        ref.organizationId(),
        userId,
        AuditAction.ENVIRONMENT_UPDATED,
        "environment",
        environmentId,
        Map.of("type", ref.type().name(), "branchFrom", previousBranch, "branchTo", branch));
    return toResponses(List.of(environment)).getFirst();
  }

  @Transactional
  public void delete(UUID environmentId, UUID userId) {
    EnvironmentRef ref = environmentAccessService.requireWrite(environmentId, userId);
    environmentRepository.delete(load(environmentId));
    audit.record(
        ref.organizationId(),
        userId,
        AuditAction.ENVIRONMENT_DELETED,
        "environment",
        environmentId,
        Map.of("type", ref.type().name(), "projectId", ref.projectId().toString()));
  }

  private static void requireKnownBranch(ProjectSnapshot project, String branch) {
    if (!project.branchNames().isEmpty() && !project.branchNames().contains(branch)) {
      throw new InvalidRequestException(
          "branch", "Branch '" + branch + "' does not exist in " + project.repositoryFullName());
    }
  }

  private Environment load(UUID environmentId) {
    return environmentRepository
        .findById(environmentId)
        .orElseThrow(() -> new ResourceNotFoundException("Environment", environmentId));
  }

  private List<EnvironmentResponse> toResponses(List<Environment> environments) {
    List<UUID> ids = environments.stream().map(Environment::getId).toList();
    Map<UUID, DeploymentConfig> configs =
        configRepository.findAllById(ids).stream()
            .collect(Collectors.toMap(DeploymentConfig::getEnvironmentId, Function.identity()));
    Map<UUID, VariableCounts> counts =
        ids.isEmpty()
            ? Map.of()
            : variableRepository.countByEnvironmentIds(ids).stream()
                .collect(Collectors.toMap(VariableCounts::getEnvironmentId, Function.identity()));
    return environments.stream()
        .map(
            environment -> {
              DeploymentConfig config = configs.get(environment.getId());
              VariableCounts count = counts.get(environment.getId());
              return new EnvironmentResponse(
                  environment.getId(),
                  environment.getProjectId(),
                  environment.getType(),
                  environment.getBranch(),
                  config == null
                      ? null
                      : DeploymentConfigResponse.from(config.settings(), config.getUpdatedAt()),
                  count == null ? 0 : count.getTotal(),
                  count == null ? 0 : count.getSecrets(),
                  environment.getCreatedAt(),
                  environment.getUpdatedAt());
            })
        .toList();
  }
}
