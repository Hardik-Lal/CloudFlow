package com.cloudflow.deployment.service;

import com.cloudflow.deployment.domain.Deployment;
import com.cloudflow.deployment.domain.DeploymentStatus;
import com.cloudflow.deployment.engine.ContainerRuntime;
import com.cloudflow.deployment.repository.DeploymentRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Deployments run in memory, so a restart interrupts any that were in flight. On startup those are
 * marked failed and their half-started containers removed; the previous deployment keeps serving.
 */
@Component
public class DeploymentRecovery {

  private static final Logger log = LoggerFactory.getLogger(DeploymentRecovery.class);

  private final DeploymentRepository repository;
  private final DeploymentStateService state;
  private final ContainerRuntime runtime;

  public DeploymentRecovery(
      DeploymentRepository repository, DeploymentStateService state, ContainerRuntime runtime) {
    this.repository = repository;
    this.state = state;
    this.runtime = runtime;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void failInterruptedDeployments() {
    List<Deployment> interrupted = repository.findAllByStatusIn(DeploymentStatus.IN_PROGRESS);
    for (Deployment deployment : interrupted) {
      log.warn("Marking interrupted deployment {} as failed", deployment.getId());
      if (deployment.getContainerId() != null) {
        try {
          runtime.remove(deployment.getContainerId());
        } catch (RuntimeException e) {
          log.warn(
              "Could not remove container of interrupted deployment {}", deployment.getId(), e);
        }
      }
      state.fail(
          deployment.getId(), "Interrupted because CloudFlow restarted during the deployment");
    }
  }
}
