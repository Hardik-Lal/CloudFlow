package com.cloudflow.deployment.domain;

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

/** One line of build, deploy, or runtime output for a deployment (append-only). */
@Entity
@Table(name = "deployment_logs")
public class DeploymentLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, updatable = false)
  private UUID deploymentId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 20)
  private LogPhase phase;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 10)
  private LogLevel level;

  @Column(nullable = false, updatable = false)
  private String message;

  @Column(nullable = false, updatable = false)
  private Instant loggedAt;

  protected DeploymentLog() {}

  public DeploymentLog(
      UUID deploymentId, LogPhase phase, LogLevel level, String message, Instant loggedAt) {
    this.deploymentId = deploymentId;
    this.phase = phase;
    this.level = level;
    this.message = message;
    this.loggedAt = loggedAt;
  }

  public Long getId() {
    return id;
  }

  public UUID getDeploymentId() {
    return deploymentId;
  }

  public LogPhase getPhase() {
    return phase;
  }

  public LogLevel getLevel() {
    return level;
  }

  public String getMessage() {
    return message;
  }

  public Instant getLoggedAt() {
    return loggedAt;
  }
}
