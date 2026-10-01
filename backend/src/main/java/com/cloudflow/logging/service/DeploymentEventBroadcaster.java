package com.cloudflow.logging.service;

import com.cloudflow.deployment.engine.DeploymentLogAppendedEvent;
import com.cloudflow.deployment.service.DeploymentStatusChangedEvent;
import com.cloudflow.logging.dto.DeploymentStatusMessage;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Pushes deployment log lines and status changes to subscribed browsers. */
@Component
public class DeploymentEventBroadcaster {

  private final SimpMessagingTemplate messaging;

  public DeploymentEventBroadcaster(SimpMessagingTemplate messaging) {
    this.messaging = messaging;
  }

  @EventListener
  public void onLogLine(DeploymentLogAppendedEvent event) {
    messaging.convertAndSend("/topic/deployments/" + event.deploymentId() + "/logs", event.line());
  }

  @TransactionalEventListener(fallbackExecution = true)
  public void onStatusChanged(DeploymentStatusChangedEvent event) {
    messaging.convertAndSend(
        "/topic/deployments/" + event.deploymentId() + "/status",
        new DeploymentStatusMessage(event.deploymentId(), event.status(), event.failureReason()));
  }
}
