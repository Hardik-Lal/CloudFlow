package com.cloudflow.environment.dto;

import com.cloudflow.environment.domain.EnvironmentType;
import java.time.Instant;
import java.util.UUID;

public record EnvironmentResponse(
    UUID id,
    UUID projectId,
    EnvironmentType type,
    String branch,
    DeploymentConfigResponse config,
    long variableCount,
    long secretCount,
    Instant createdAt,
    Instant updatedAt) {}
