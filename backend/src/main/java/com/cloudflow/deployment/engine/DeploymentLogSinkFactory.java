package com.cloudflow.deployment.engine;

import com.cloudflow.deployment.repository.DeploymentLogRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class DeploymentLogSinkFactory {

  private final DeploymentLogRepository repository;
  private final Clock clock;
  private final ApplicationEventPublisher events;

  public DeploymentLogSinkFactory(
      DeploymentLogRepository repository, Clock clock, ApplicationEventPublisher events) {
    this.repository = repository;
    this.clock = clock;
    this.events = events;
  }

  public DeploymentLogSink create(UUID deploymentId, SecretMasker masker) {
    return new DeploymentLogSink(deploymentId, repository, masker, clock, events);
  }
}
