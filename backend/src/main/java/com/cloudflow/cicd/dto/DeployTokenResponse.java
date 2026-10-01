package com.cloudflow.cicd.dto;

import com.cloudflow.cicd.domain.DeployToken;
import java.time.Instant;
import java.util.UUID;

public record DeployTokenResponse(
    UUID id,
    UUID environmentId,
    String name,
    String tokenPrefix,
    UUID createdBy,
    Instant createdAt,
    Instant lastUsedAt,
    Instant revokedAt) {

  public static DeployTokenResponse from(DeployToken token) {
    return new DeployTokenResponse(
        token.getId(),
        token.getEnvironmentId(),
        token.getName(),
        token.getTokenPrefix(),
        token.getCreatedBy(),
        token.getCreatedAt(),
        token.getLastUsedAt(),
        token.getRevokedAt());
  }
}
