package com.cloudflow.deployment.dto;

import com.cloudflow.environment.domain.DeploymentTarget;
import java.util.UUID;

/** The deployment currently serving an environment, as seen by other modules. */
public record ActiveDeployment(
    UUID deploymentId,
    UUID environmentId,
    UUID projectId,
    DeploymentTarget target,
    String containerId,
    String containerName,
    Integer hostPort) {}
