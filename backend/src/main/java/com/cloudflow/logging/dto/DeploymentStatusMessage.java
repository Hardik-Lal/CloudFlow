package com.cloudflow.logging.dto;

import com.cloudflow.deployment.domain.DeploymentStatus;
import java.util.UUID;

public record DeploymentStatusMessage(
    UUID deploymentId, DeploymentStatus status, String failureReason) {}
