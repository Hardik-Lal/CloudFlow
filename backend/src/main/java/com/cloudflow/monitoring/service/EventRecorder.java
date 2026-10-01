package com.cloudflow.monitoring.service;

import com.cloudflow.deployment.service.DeploymentStatusChangedEvent;
import com.cloudflow.monitoring.domain.EnvironmentEvent;
import com.cloudflow.monitoring.domain.EventSeverity;
import com.cloudflow.monitoring.domain.EventType;
import com.cloudflow.monitoring.repository.EnvironmentEventRepository;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/** Records the environment timeline: deployment outcomes and runtime state changes. */
@Service
public class EventRecorder {

  private final EnvironmentEventRepository repository;
  private final Clock clock;

  public EventRecorder(EnvironmentEventRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(
      UUID environmentId,
      UUID deploymentId,
      EventType type,
      EventSeverity severity,
      String message) {
    repository.save(
        new EnvironmentEvent(
            environmentId, deploymentId, type, severity, message, clock.instant()));
  }

  /**
   * Runs after the deployment transaction commits; {@code REQUIRES_NEW} gives the insert its own
   * transaction because the triggering one has already finished.
   */
  @TransactionalEventListener(fallbackExecution = true)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onDeploymentStatus(DeploymentStatusChangedEvent event) {
    String commit =
        event.commitSha() == null ? "the branch head" : event.commitSha().substring(0, 7);
    String trigger = event.triggerType().name().toLowerCase(Locale.ROOT);
    switch (event.status()) {
      case BUILDING ->
          save(
              event,
              EventType.DEPLOYMENT_STARTED,
              EventSeverity.INFO,
              "Deployment of " + commit + " started (" + trigger + ")");
      case SUCCEEDED ->
          save(
              event,
              EventType.DEPLOYMENT_SUCCEEDED,
              EventSeverity.INFO,
              "Deployment of " + commit + " is live (" + trigger + ")");
      case FAILED ->
          save(
              event,
              EventType.DEPLOYMENT_FAILED,
              EventSeverity.ERROR,
              "Deployment of " + commit + " failed: " + event.failureReason());
      case CANCELLED ->
          save(
              event,
              EventType.DEPLOYMENT_CANCELLED,
              EventSeverity.WARN,
              "Deployment of " + commit + " was cancelled");
      case ROLLED_BACK ->
          save(
              event,
              EventType.DEPLOYMENT_ROLLED_BACK,
              EventSeverity.WARN,
              "Deployment of " + commit + " was replaced by a rollback");
      default -> {
        // Intermediate states (QUEUED, DEPLOYING, HEALTH_CHECK) are visible on the deployment.
      }
    }
  }

  private void save(
      DeploymentStatusChangedEvent event, EventType type, EventSeverity severity, String message) {
    repository.save(
        new EnvironmentEvent(
            event.environmentId(), event.deploymentId(), type, severity, message, clock.instant()));
  }
}
