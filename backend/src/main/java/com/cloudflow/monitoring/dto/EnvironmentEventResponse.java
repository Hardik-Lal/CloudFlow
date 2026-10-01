package com.cloudflow.monitoring.dto;

import com.cloudflow.monitoring.domain.EnvironmentEvent;
import com.cloudflow.monitoring.domain.EventSeverity;
import com.cloudflow.monitoring.domain.EventType;
import java.time.Instant;
import java.util.UUID;

public record EnvironmentEventResponse(
    long id,
    EventType type,
    EventSeverity severity,
    String message,
    UUID deploymentId,
    Instant createdAt) {

  public static EnvironmentEventResponse from(EnvironmentEvent event) {
    return new EnvironmentEventResponse(
        event.getId(),
        event.getType(),
        event.getSeverity(),
        event.getMessage(),
        event.getDeploymentId(),
        event.getCreatedAt());
  }
}
