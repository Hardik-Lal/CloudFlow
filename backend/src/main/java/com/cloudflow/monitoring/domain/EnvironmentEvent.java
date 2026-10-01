package com.cloudflow.monitoring.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Something notable that happened to an environment (append-only). */
@Entity
@Table(name = "environment_events")
public class EnvironmentEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, updatable = false)
  private UUID environmentId;

  @Column(updatable = false)
  private UUID deploymentId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 40)
  private EventType type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 10)
  private EventSeverity severity;

  @Column(nullable = false, updatable = false, length = 1000)
  private String message;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  protected EnvironmentEvent() {}

  public EnvironmentEvent(
      UUID environmentId,
      UUID deploymentId,
      EventType type,
      EventSeverity severity,
      String message,
      Instant createdAt) {
    this.environmentId = environmentId;
    this.deploymentId = deploymentId;
    this.type = type;
    this.severity = severity;
    this.message = message.length() > 1000 ? message.substring(0, 1000) : message;
    this.createdAt = createdAt;
  }

  public Long getId() {
    return id;
  }

  public UUID getEnvironmentId() {
    return environmentId;
  }

  public UUID getDeploymentId() {
    return deploymentId;
  }

  public EventType getType() {
    return type;
  }

  public EventSeverity getSeverity() {
    return severity;
  }

  public String getMessage() {
    return message;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
