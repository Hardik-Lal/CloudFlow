package com.cloudflow.environment.dto;

import com.cloudflow.environment.domain.ConfigTemplate;
import com.cloudflow.environment.domain.DeploymentSettings;
import com.cloudflow.environment.domain.DeploymentTarget;
import java.math.BigDecimal;
import java.time.Instant;

public record DeploymentConfigResponse(
    ConfigTemplate template,
    String runtimeVersion,
    String buildCommand,
    String startCommand,
    String dockerfilePath,
    int containerPort,
    String healthCheckPath,
    BigDecimal cpuLimit,
    Integer memoryLimitMb,
    DeploymentTarget target,
    Instant updatedAt) {

  public static DeploymentConfigResponse from(DeploymentSettings settings, Instant updatedAt) {
    return new DeploymentConfigResponse(
        settings.template(),
        settings.runtimeVersion(),
        settings.buildCommand(),
        settings.startCommand(),
        settings.dockerfilePath(),
        settings.containerPort(),
        settings.healthCheckPath(),
        settings.cpuLimit(),
        settings.memoryLimitMb(),
        settings.target(),
        updatedAt);
  }
}
