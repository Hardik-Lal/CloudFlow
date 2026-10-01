package com.cloudflow.environment.dto;

import com.cloudflow.environment.domain.EnvironmentType;
import java.util.UUID;

/** Read-only environment facts for other modules. */
public record EnvironmentSnapshot(UUID id, UUID projectId, EnvironmentType type, String branch) {}
