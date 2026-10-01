package com.cloudflow.logging.service;

import com.cloudflow.deployment.dto.ActiveDeployment;
import com.cloudflow.deployment.engine.ContainerRuntime;
import com.cloudflow.deployment.engine.EnvironmentSecretMasker;
import com.cloudflow.deployment.engine.SecretMasker;
import com.cloudflow.deployment.service.DeploymentQueryService;
import com.cloudflow.environment.service.EnvironmentAccessService;
import com.cloudflow.logging.dto.RuntimeLogLine;
import com.cloudflow.organization.domain.Permission;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Recent output of an environment's running container (history before live streaming). */
@Service
public class RuntimeLogService {

  static final int MAX_TAIL = 1000;

  private final EnvironmentAccessService environmentAccess;
  private final DeploymentQueryService deployments;
  private final ContainerRuntime runtime;
  private final EnvironmentSecretMasker secretMasker;
  private final Clock clock;

  public RuntimeLogService(
      EnvironmentAccessService environmentAccess,
      DeploymentQueryService deployments,
      ContainerRuntime runtime,
      EnvironmentSecretMasker secretMasker,
      Clock clock) {
    this.environmentAccess = environmentAccess;
    this.deployments = deployments;
    this.runtime = runtime;
    this.secretMasker = secretMasker;
    this.clock = clock;
  }

  public List<RuntimeLogLine> recent(UUID environmentId, UUID userId, int tail) {
    environmentAccess.require(
        environmentId, userId, Permission.DEPLOYMENT_READ, Permission.DEPLOYMENT_READ);
    Optional<ActiveDeployment> active = deployments.activeFor(environmentId);
    if (active.isEmpty() || active.get().containerId() == null) {
      return List.of();
    }
    SecretMasker masker = secretMasker.forEnvironment(environmentId);
    Instant now = clock.instant();
    return runtime.tailLogs(active.get().containerId(), Math.clamp(tail, 1, MAX_TAIL)).stream()
        .map(line -> new RuntimeLogLine(active.get().deploymentId(), masker.mask(line), now))
        .toList();
  }
}
