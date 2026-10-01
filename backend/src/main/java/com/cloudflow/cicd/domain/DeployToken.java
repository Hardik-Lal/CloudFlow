package com.cloudflow.cicd.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Credential that lets a CI pipeline deploy one environment. Only a SHA-256 hash is stored; the
 * token acts with the permissions of the user who created it, checked on every use.
 */
@Entity
@Table(name = "deploy_tokens")
public class DeployToken {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, updatable = false)
  private UUID environmentId;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(nullable = false, unique = true, updatable = false, length = 64)
  private String tokenHash;

  /** First characters of the token, shown so users can tell tokens apart. */
  @Column(nullable = false, updatable = false, length = 12)
  private String tokenPrefix;

  @Column(updatable = false)
  private UUID createdBy;

  private Instant lastUsedAt;

  private Instant revokedAt;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  protected DeployToken() {}

  public DeployToken(
      UUID environmentId, String name, String tokenHash, String tokenPrefix, UUID createdBy) {
    this.environmentId = environmentId;
    this.name = name;
    this.tokenHash = tokenHash;
    this.tokenPrefix = tokenPrefix;
    this.createdBy = createdBy;
  }

  public boolean isRevoked() {
    return revokedAt != null;
  }

  public void revoke(Instant at) {
    if (revokedAt == null) {
      revokedAt = at;
    }
  }

  public void markUsed(Instant at) {
    this.lastUsedAt = at;
  }

  public UUID getId() {
    return id;
  }

  public UUID getEnvironmentId() {
    return environmentId;
  }

  public String getName() {
    return name;
  }

  public String getTokenPrefix() {
    return tokenPrefix;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public Instant getLastUsedAt() {
    return lastUsedAt;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
