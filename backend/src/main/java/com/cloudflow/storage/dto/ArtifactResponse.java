package com.cloudflow.storage.dto;

import com.cloudflow.storage.domain.Artifact;
import com.cloudflow.storage.domain.ArtifactKind;
import java.time.Instant;
import java.util.UUID;

public record ArtifactResponse(
    UUID id,
    UUID projectId,
    UUID environmentId,
    UUID deploymentId,
    ArtifactKind kind,
    String name,
    String contentType,
    long sizeBytes,
    String sha256,
    UUID createdBy,
    Instant createdAt) {

  public static ArtifactResponse from(Artifact artifact) {
    return new ArtifactResponse(
        artifact.getId(),
        artifact.getProjectId(),
        artifact.getEnvironmentId(),
        artifact.getDeploymentId(),
        artifact.getKind(),
        artifact.getName(),
        artifact.getContentType(),
        artifact.getSizeBytes(),
        artifact.getSha256(),
        artifact.getCreatedBy(),
        artifact.getCreatedAt());
  }
}
