package com.cloudflow.deployment.dto;

import com.cloudflow.deployment.domain.Deployment;
import com.cloudflow.deployment.domain.DeploymentStatus;
import com.cloudflow.deployment.domain.ScanStatus;
import com.cloudflow.deployment.domain.TriggerType;
import com.cloudflow.environment.domain.DeploymentTarget;
import com.cloudflow.project.domain.AppType;
import java.time.Instant;
import java.util.UUID;

/**
 * @param url where the deployed application is reachable (only while it is running)
 */
public record DeploymentResponse(
    UUID id,
    UUID projectId,
    UUID environmentId,
    DeploymentStatus status,
    TriggerType triggerType,
    UUID triggeredBy,
    String branch,
    String commitSha,
    String commitMessage,
    AppType appType,
    String imageTag,
    String imageId,
    String containerName,
    Integer hostPort,
    String url,
    UUID rollbackOfId,
    boolean active,
    String failureReason,
    Instant createdAt,
    Instant startedAt,
    Instant finishedAt,
    Long pipelineRunId,
    ScanStatus scanStatus,
    Integer vulnerabilitiesCritical,
    Integer vulnerabilitiesHigh,
    DeploymentTarget target) {

  public static DeploymentResponse from(Deployment deployment, String publicHost) {
    boolean reachable = deployment.isActive() && deployment.getHostPort() != null;
    return new DeploymentResponse(
        deployment.getId(),
        deployment.getProjectId(),
        deployment.getEnvironmentId(),
        deployment.getStatus(),
        deployment.getTriggerType(),
        deployment.getTriggeredBy(),
        deployment.getBranch(),
        deployment.getCommitSha(),
        deployment.getCommitMessage(),
        deployment.getAppType(),
        deployment.getImageTag(),
        deployment.getImageId(),
        deployment.getContainerName(),
        deployment.getHostPort(),
        reachable ? "http://" + publicHost + ":" + deployment.getHostPort() : null,
        deployment.getRollbackOfId(),
        deployment.isActive(),
        deployment.getFailureReason(),
        deployment.getCreatedAt(),
        deployment.getStartedAt(),
        deployment.getFinishedAt(),
        deployment.getPipelineRunId(),
        deployment.getScanStatus(),
        deployment.getVulnerabilitiesCritical(),
        deployment.getVulnerabilitiesHigh(),
        deployment.getTarget());
  }
}
