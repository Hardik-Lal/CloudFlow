package com.cloudflow.storage.domain;

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
import org.hibernate.annotations.CreationTimestamp;

/** Metadata of an object in the artifact bucket (append-only). */
@Entity
@Table(name = "artifacts")
public class Artifact {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, updatable = false)
  private UUID projectId;

  @Column(updatable = false)
  private UUID environmentId;

  @Column(updatable = false)
  private UUID deploymentId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, updatable = false, length = 20)
  private ArtifactKind kind;

  @Column(nullable = false, updatable = false)
  private String name;

  @Column(nullable = false, updatable = false, length = 512)
  private String storageKey;

  @Column(nullable = false, updatable = false, length = 100)
  private String contentType;

  @Column(nullable = false, updatable = false)
  private long sizeBytes;

  @Column(nullable = false, updatable = false, length = 64)
  private String sha256;

  @Column(updatable = false)
  private UUID createdBy;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  protected Artifact() {}

  public Artifact(
      UUID projectId,
      UUID environmentId,
      UUID deploymentId,
      ArtifactKind kind,
      String name,
      String storageKey,
      String contentType,
      long sizeBytes,
      String sha256,
      UUID createdBy) {
    this.projectId = projectId;
    this.environmentId = environmentId;
    this.deploymentId = deploymentId;
    this.kind = kind;
    this.name = name;
    this.storageKey = storageKey;
    this.contentType = contentType;
    this.sizeBytes = sizeBytes;
    this.sha256 = sha256;
    this.createdBy = createdBy;
  }

  public UUID getId() {
    return id;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public UUID getEnvironmentId() {
    return environmentId;
  }

  public UUID getDeploymentId() {
    return deploymentId;
  }

  public ArtifactKind getKind() {
    return kind;
  }

  public String getName() {
    return name;
  }

  public String getStorageKey() {
    return storageKey;
  }

  public String getContentType() {
    return contentType;
  }

  public long getSizeBytes() {
    return sizeBytes;
  }

  public String getSha256() {
    return sha256;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
