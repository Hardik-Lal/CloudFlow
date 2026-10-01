package com.cloudflow.monitoring.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One periodic HTTP probe of a running deployment (append-only). */
@Entity
@Table(name = "health_check_results")
public class HealthCheckResult {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, updatable = false)
  private UUID environmentId;

  @Column(nullable = false, updatable = false)
  private UUID deploymentId;

  @Column(nullable = false, updatable = false)
  private boolean healthy;

  @Column(updatable = false)
  private Integer statusCode;

  @Column(nullable = false, updatable = false)
  private int responseTimeMs;

  @Column(updatable = false, length = 500)
  private String error;

  @Column(nullable = false, updatable = false)
  private Instant checkedAt;

  protected HealthCheckResult() {}

  public HealthCheckResult(
      UUID environmentId,
      UUID deploymentId,
      boolean healthy,
      Integer statusCode,
      int responseTimeMs,
      String error,
      Instant checkedAt) {
    this.environmentId = environmentId;
    this.deploymentId = deploymentId;
    this.healthy = healthy;
    this.statusCode = statusCode;
    this.responseTimeMs = responseTimeMs;
    this.error = error != null && error.length() > 500 ? error.substring(0, 500) : error;
    this.checkedAt = checkedAt;
  }

  public UUID getEnvironmentId() {
    return environmentId;
  }

  public UUID getDeploymentId() {
    return deploymentId;
  }

  public boolean isHealthy() {
    return healthy;
  }

  public Integer getStatusCode() {
    return statusCode;
  }

  public int getResponseTimeMs() {
    return responseTimeMs;
  }

  public String getError() {
    return error;
  }

  public Instant getCheckedAt() {
    return checkedAt;
  }
}
