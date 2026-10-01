package com.cloudflow.environment.dto;

import com.cloudflow.environment.domain.ConfigTemplate;
import com.cloudflow.environment.domain.DeploymentSettings;
import com.cloudflow.environment.domain.DeploymentTarget;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Shape validation only; deployability is checked by the configuration validator. */
public record DeploymentConfigRequest(
    @NotNull ConfigTemplate template,
    @Size(max = 20) String runtimeVersion,
    @Size(max = 1000) String buildCommand,
    @Size(max = 1000) String startCommand,
    @NotBlank @Size(max = 255) String dockerfilePath,
    @Min(1) @Max(65535) int containerPort,
    @NotBlank @Size(max = 255) String healthCheckPath,
    @DecimalMin("0.10") @DecimalMax("8.00") BigDecimal cpuLimit,
    @Min(128) @Max(16384) Integer memoryLimitMb,
    DeploymentTarget target) {

  public DeploymentSettings toSettings() {
    return new DeploymentSettings(
        template,
        blankToNull(runtimeVersion),
        blankToNull(buildCommand),
        blankToNull(startCommand),
        dockerfilePath.strip(),
        containerPort,
        healthCheckPath.strip(),
        cpuLimit,
        memoryLimitMb,
        target);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
