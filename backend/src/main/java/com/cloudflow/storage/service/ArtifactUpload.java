package com.cloudflow.storage.service;

import com.cloudflow.storage.domain.ArtifactKind;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * An artifact to store.
 *
 * @param environmentId optional
 * @param deploymentId optional
 * @param createdBy user whose action produced the artifact; optional
 */
public record ArtifactUpload(
    UUID projectId,
    UUID environmentId,
    UUID deploymentId,
    ArtifactKind kind,
    String name,
    String contentType,
    byte[] content,
    UUID createdBy) {

  public static ArtifactUpload text(
      UUID projectId,
      UUID environmentId,
      UUID deploymentId,
      ArtifactKind kind,
      String name,
      String contentType,
      String content,
      UUID createdBy) {
    return new ArtifactUpload(
        projectId,
        environmentId,
        deploymentId,
        kind,
        name,
        contentType,
        content.getBytes(StandardCharsets.UTF_8),
        createdBy);
  }
}
