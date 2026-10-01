package com.cloudflow.environment.dto;

import com.cloudflow.environment.domain.EnvironmentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param branch optional; defaults to the repository's default branch
 */
public record CreateEnvironmentRequest(
    @NotNull EnvironmentType type, @Size(max = 255) String branch) {}
