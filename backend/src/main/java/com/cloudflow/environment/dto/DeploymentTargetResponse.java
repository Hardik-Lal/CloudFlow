package com.cloudflow.environment.dto;

import com.cloudflow.environment.domain.DeploymentTarget;

/** A deployment target and whether this installation is configured for it. */
public record DeploymentTargetResponse(DeploymentTarget target, String label, boolean available) {}
