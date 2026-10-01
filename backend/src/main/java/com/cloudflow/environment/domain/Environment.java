package com.cloudflow.environment.domain;

import com.cloudflow.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

/** A deployment target of a project: Development, Staging, or Production (one of each). */
@Entity
@Table(name = "environments")
public class Environment extends BaseEntity {

  @Column(nullable = false, updatable = false)
  private UUID projectId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 20)
  private EnvironmentType type;

  /** Git branch deployed to this environment. */
  @Column(nullable = false)
  private String branch;

  protected Environment() {}

  public Environment(UUID projectId, EnvironmentType type, String branch) {
    this.projectId = projectId;
    this.type = type;
    this.branch = branch;
  }

  public void changeBranch(String branch) {
    this.branch = branch;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public EnvironmentType getType() {
    return type;
  }

  public String getBranch() {
    return branch;
  }
}
