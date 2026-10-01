package com.cloudflow.deployment.service;

import com.cloudflow.deployment.engine.DeploymentRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Hands queued deployments to the bounded deployment executor. Dispatching after commit guarantees
 * the worker always sees the persisted deployment.
 */
@Component
public class DeploymentDispatcher {

  private static final Logger log = LoggerFactory.getLogger(DeploymentDispatcher.class);

  private final TaskExecutor executor;
  private final DeploymentRunner runner;
  private final DeploymentStateService state;

  public DeploymentDispatcher(
      @Qualifier("deploymentTaskExecutor") TaskExecutor executor,
      DeploymentRunner runner,
      DeploymentStateService state) {
    this.executor = executor;
    this.runner = runner;
    this.state = state;
  }

  @TransactionalEventListener
  public void onQueued(DeploymentQueuedEvent event) {
    try {
      executor.execute(() -> runner.run(event.deploymentId()));
    } catch (TaskRejectedException e) {
      log.warn("Deployment queue is full; rejecting {}", event.deploymentId());
      state.fail(event.deploymentId(), "The deployment queue is full; try again shortly");
    }
  }
}
