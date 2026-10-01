package com.cloudflow.project.domain;

import com.cloudflow.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Branch metadata synced from GitHub. */
@Entity
@Table(name = "branches")
public class Branch extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "repository_id", nullable = false, updatable = false)
  private SourceRepository repository;

  @Column(nullable = false, updatable = false)
  private String name;

  @Column(nullable = false, length = 40)
  private String headCommitSha;

  @Column(name = "protected", nullable = false)
  private boolean isProtected;

  protected Branch() {}

  Branch(SourceRepository repository, String name, String headCommitSha, boolean isProtected) {
    this.repository = repository;
    this.name = name;
    this.headCommitSha = headCommitSha;
    this.isProtected = isProtected;
  }

  void update(String headCommitSha, boolean isProtected) {
    this.headCommitSha = headCommitSha;
    this.isProtected = isProtected;
  }

  public String getName() {
    return name;
  }

  public String getHeadCommitSha() {
    return headCommitSha;
  }

  public boolean isProtected() {
    return isProtected;
  }
}
