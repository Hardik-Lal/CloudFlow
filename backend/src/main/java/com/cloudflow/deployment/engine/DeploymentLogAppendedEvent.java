package com.cloudflow.deployment.engine;

import com.cloudflow.deployment.dto.DeploymentLogResponse;
import java.util.UUID;

/** Published after a deployment log line is stored (already masked). */
public record DeploymentLogAppendedEvent(UUID deploymentId, DeploymentLogResponse line) {}
