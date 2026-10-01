package com.cloudflow.environment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** How an environment is built and run. One per environment (shares its id). */
@Entity
@Table(name = "deployment_configs")
public class DeploymentConfig {

  @Id private UUID environmentId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ConfigTemplate template;

  @Column(length = 20)
  private String runtimeVersion;

  @Column(length = 1000)
  private String buildCommand;

  @Column(length = 1000)
  private String startCommand;

  @Column(nullable = false)
  private String dockerfilePath;

  @Column(nullable = false)
  private int containerPort;

  @Column(nullable = false)
  private String healthCheckPath;

  @Column(precision = 4, scale = 2)
  private BigDecimal cpuLimit;

  private Integer memoryLimitMb;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private DeploymentTarget target;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(nullable = false)
  private Instant updatedAt;

  protected DeploymentConfig() {}

  public DeploymentConfig(UUID environmentId, DeploymentSettings settings) {
    this.environmentId = environmentId;
    this.template = settings.template();
    this.runtimeVersion = settings.runtimeVersion();
    this.buildCommand = settings.buildCommand();
    this.startCommand = settings.startCommand();
    this.dockerfilePath = settings.dockerfilePath();
    this.containerPort = settings.containerPort();
    this.healthCheckPath = settings.healthCheckPath();
    this.cpuLimit = settings.cpuLimit();
    this.memoryLimitMb = settings.memoryLimitMb();
    this.target = settings.target();
  }

  public void apply(DeploymentSettings settings) {
    this.template = settings.template();
    this.runtimeVersion = settings.runtimeVersion();
    this.buildCommand = settings.buildCommand();
    this.startCommand = settings.startCommand();
    this.dockerfilePath = settings.dockerfilePath();
    this.containerPort = settings.containerPort();
    this.healthCheckPath = settings.healthCheckPath();
    this.cpuLimit = settings.cpuLimit();
    this.memoryLimitMb = settings.memoryLimitMb();
    this.target = settings.target();
  }

  public DeploymentSettings settings() {
    return new DeploymentSettings(
        template,
        runtimeVersion,
        buildCommand,
        startCommand,
        dockerfilePath,
        containerPort,
        healthCheckPath,
        cpuLimit,
        memoryLimitMb,
        target);
  }

  public UUID getEnvironmentId() {
    return environmentId;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
