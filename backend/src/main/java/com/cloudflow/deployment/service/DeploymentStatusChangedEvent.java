package com.cloudflow.deployment.service;

import com.cloudflow.deployment.domain.DeploymentStatus;
import com.cloudflow.deployment.domain.TriggerType;
import java.util.UUID;

/**
 * Published whenever a deployment changes status. Listeners should use
 * {@code @TransactionalEventListener(fallbackExecution = true)} so they see committed state.
 */
public record DeploymentStatusChangedEvent(
    UUID deploymentId,
    UUID environmentId,
    UUID projectId,
    DeploymentStatus status,
    TriggerType triggerType,
    String commitSha,
    String failureReason) {}
