package com.cloudflow.deployment.dto;

import com.cloudflow.deployment.domain.DeploymentLog;
import com.cloudflow.deployment.domain.LogLevel;
import com.cloudflow.deployment.domain.LogPhase;
import java.time.Instant;

public record DeploymentLogResponse(
    long id, LogPhase phase, LogLevel level, String message, Instant loggedAt) {

  public static DeploymentLogResponse from(DeploymentLog log) {
    return new DeploymentLogResponse(
        log.getId(), log.getPhase(), log.getLevel(), log.getMessage(), log.getLoggedAt());
  }
}
