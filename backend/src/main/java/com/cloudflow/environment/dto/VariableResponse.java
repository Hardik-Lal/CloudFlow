package com.cloudflow.environment.dto;

import com.cloudflow.environment.domain.EnvironmentVariable;
import java.time.Instant;

/**
 * @param value the plain value, or {@code null} for secrets (secret values are write-only)
 */
public record VariableResponse(String key, String value, boolean secret, Instant updatedAt) {

  public static VariableResponse from(EnvironmentVariable variable) {
    return new VariableResponse(
        variable.getKey(),
        variable.isSecret() ? null : variable.getStoredValue(),
        variable.isSecret(),
        variable.getUpdatedAt());
  }
}
