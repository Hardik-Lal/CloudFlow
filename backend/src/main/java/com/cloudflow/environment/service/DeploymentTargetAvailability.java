package com.cloudflow.environment.service;

import com.cloudflow.environment.domain.DeploymentTarget;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Which deployment targets this CloudFlow installation is configured for. Docker always is. */
@Component
public class DeploymentTargetAvailability {

  private final boolean kubernetesEnabled;

  public DeploymentTargetAvailability(
      @Value("${cloudflow.kubernetes.enabled:false}") boolean kubernetesEnabled) {
    this.kubernetesEnabled = kubernetesEnabled;
  }

  public boolean isAvailable(DeploymentTarget target) {
    return target == DeploymentTarget.DOCKER || kubernetesEnabled;
  }
}
