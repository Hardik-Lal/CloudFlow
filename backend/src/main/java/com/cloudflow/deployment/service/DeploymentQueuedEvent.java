package com.cloudflow.deployment.service;

import java.util.UUID;

/** Published when a deployment is queued; it is dispatched once the transaction commits. */
public record DeploymentQueuedEvent(UUID deploymentId) {}
