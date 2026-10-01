package com.cloudflow.environment.service;

import com.cloudflow.audit.domain.AuditAction;
import com.cloudflow.audit.service.AuditLogger;
import com.cloudflow.common.exception.ResourceNotFoundException;
import com.cloudflow.environment.domain.ConfigTemplate;
import com.cloudflow.environment.domain.DeploymentConfig;
import com.cloudflow.environment.domain.DeploymentSettings;
import com.cloudflow.environment.domain.DeploymentTarget;
import com.cloudflow.environment.domain.Environment;
import com.cloudflow.environment.dto.ConfigTemplateResponse;
import com.cloudflow.environment.dto.DeploymentConfigRequest;
import com.cloudflow.environment.dto.DeploymentConfigResponse;
import com.cloudflow.environment.dto.DeploymentTargetResponse;
import com.cloudflow.environment.dto.EnvironmentRef;
import com.cloudflow.environment.dto.ValidationResult;
import com.cloudflow.environment.repository.DeploymentConfigRepository;
import com.cloudflow.environment.repository.EnvironmentRepository;
import com.cloudflow.project.domain.AppType;
import com.cloudflow.project.dto.ProjectSnapshot;
import com.cloudflow.project.service.ProjectLookupService;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeploymentConfigService {

  private final AuditLogger audit;

  private final DeploymentConfigRepository configRepository;
  private final EnvironmentRepository environmentRepository;
  private final EnvironmentAccessService accessService;
  private final ProjectLookupService projectLookupService;
  private final ConfigurationValidator validator;
  private final DeploymentTargetAvailability targetAvailability;

  public DeploymentConfigService(
      DeploymentConfigRepository configRepository,
      EnvironmentRepository environmentRepository,
      EnvironmentAccessService accessService,
      ProjectLookupService projectLookupService,
      ConfigurationValidator validator,
      DeploymentTargetAvailability targetAvailability,
      AuditLogger audit) {
    this.targetAvailability = targetAvailability;
    this.audit = audit;
    this.configRepository = configRepository;
    this.environmentRepository = environmentRepository;
    this.accessService = accessService;
    this.projectLookupService = projectLookupService;
    this.validator = validator;
  }

  @Transactional(readOnly = true)
  public DeploymentConfigResponse get(UUID environmentId, UUID userId) {
    accessService.requireRead(environmentId, userId);
    DeploymentConfig config = load(environmentId);
    return DeploymentConfigResponse.from(config.settings(), config.getUpdatedAt());
  }

  /**
   * Saves the configuration. Semantically invalid configurations can still be saved (so work can be
   * saved in progress) but cannot be deployed; see {@link #validateForDeployment}.
   */
  @Transactional
  public DeploymentConfigResponse update(
      UUID environmentId, UUID userId, DeploymentConfigRequest request) {
    EnvironmentRef environment = accessService.requireWrite(environmentId, userId);
    DeploymentConfig config = load(environmentId);
    config.apply(request.toSettings());
    audit.record(
        environment.organizationId(),
        userId,
        AuditAction.DEPLOYMENT_CONFIG_UPDATED,
        "environment",
        environmentId,
        Map.of(
            "environment", environment.type().name(),
            "template", request.template().name(),
            "target", config.settings().target().name(),
            "containerPort", String.valueOf(request.containerPort())));
    configRepository.flush();
    return DeploymentConfigResponse.from(config.settings(), config.getUpdatedAt());
  }

  @Transactional(readOnly = true)
  public ValidationResult validate(UUID environmentId, UUID userId) {
    accessService.requireRead(environmentId, userId);
    return validateForDeployment(environmentId);
  }

  /** Validation used as a deployment gate. Internal: the caller must have authorized the user. */
  @Transactional(readOnly = true)
  public ValidationResult validateForDeployment(UUID environmentId) {
    Environment environment =
        environmentRepository
            .findById(environmentId)
            .orElseThrow(() -> new ResourceNotFoundException("Environment", environmentId));
    ProjectSnapshot project = projectLookupService.snapshot(environment.getProjectId());
    return validator.validate(
        load(environmentId).settings(),
        project.appType(),
        environment.getBranch(),
        project.branchNames());
  }

  /** Current settings for a deployment. Internal: the caller must have authorized the user. */
  @Transactional(readOnly = true)
  public DeploymentSettings settings(UUID environmentId) {
    return load(environmentId).settings();
  }

  /** Deployment targets and whether this installation can deploy to them. */
  public List<DeploymentTargetResponse> targets() {
    return Arrays.stream(DeploymentTarget.values())
        .map(
            target ->
                new DeploymentTargetResponse(
                    target, target.label(), targetAvailability.isAvailable(target)))
        .toList();
  }

  public List<ConfigTemplateResponse> templates(AppType appType) {
    ConfigTemplate recommended = ConfigTemplate.recommendedFor(appType);
    return Arrays.stream(ConfigTemplate.values())
        .map(
            template ->
                new ConfigTemplateResponse(
                    template,
                    template.label(),
                    template.description(),
                    template == recommended,
                    DeploymentConfigResponse.from(
                        DeploymentSettings.defaults(template, appType), null)))
        .toList();
  }

  private DeploymentConfig load(UUID environmentId) {
    return configRepository
        .findById(environmentId)
        .orElseThrow(
            () -> new ResourceNotFoundException("Deployment configuration", environmentId));
  }
}
