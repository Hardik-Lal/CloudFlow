package com.cloudflow.storage.service;

import com.cloudflow.common.exception.ExternalServiceException;
import com.cloudflow.common.exception.ResourceNotFoundException;
import com.cloudflow.common.web.PageResponse;
import com.cloudflow.organization.domain.Permission;
import com.cloudflow.project.service.ProjectAccessService;
import com.cloudflow.project.service.ProjectDeletedEvent;
import com.cloudflow.storage.config.StorageProperties;
import com.cloudflow.storage.domain.Artifact;
import com.cloudflow.storage.domain.ArtifactKind;
import com.cloudflow.storage.dto.ArtifactResponse;
import com.cloudflow.storage.repository.ArtifactRepository;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Stores and serves artifacts. Storing is best effort: a storage outage never fails the deployment,
 * commit, or approval that produced the artifact, it is only logged.
 */
@Service
public class ArtifactService {

  private static final String STORAGE = "Artifact storage";
  private static final Logger log = LoggerFactory.getLogger(ArtifactService.class);

  private final ObjectProvider<ObjectStorage> storageProvider;
  private final ArtifactRepository repository;
  private final ProjectAccessService projectAccess;
  private final StorageProperties properties;
  private final Executor executor;

  public ArtifactService(
      ObjectProvider<ObjectStorage> storageProvider,
      ArtifactRepository repository,
      ProjectAccessService projectAccess,
      StorageProperties properties,
      @Qualifier("artifactTaskExecutor") Executor executor) {
    this.executor = executor;
    this.storageProvider = storageProvider;
    this.repository = repository;
    this.projectAccess = projectAccess;
    this.properties = properties;
  }

  public boolean isEnabled() {
    return storageProvider.getIfAvailable() != null;
  }

  /** {@link #store} on a background thread; the content is produced there too. */
  public void storeAsync(Supplier<ArtifactUpload> upload) {
    if (!isEnabled()) {
      return;
    }
    try {
      executor.execute(
          () -> {
            try {
              store(upload.get());
            } catch (RuntimeException e) {
              log.warn("Could not prepare an artifact: {}", e.getMessage());
            }
          });
    } catch (RejectedExecutionException e) {
      log.warn("Artifact upload queue is full; an artifact was not stored");
    }
  }

  /**
   * Uploads the content and records it. Internal: the caller has authorized the action that
   * produced the artifact.
   *
   * @return the stored artifact, or empty if storage is disabled or failed
   */
  public Optional<ArtifactResponse> store(ArtifactUpload upload) {
    ObjectStorage storage = storageProvider.getIfAvailable();
    if (storage == null) {
      return Optional.empty();
    }
    long maxBytes = properties.maxObjectSizeMb() * 1024L * 1024L;
    if (upload.content().length > maxBytes) {
      log.warn(
          "Not storing {} of project {}: {} bytes exceeds the {} MB limit",
          upload.name(),
          upload.projectId(),
          upload.content().length,
          properties.maxObjectSizeMb());
      return Optional.empty();
    }
    String key =
        projectPrefix(upload.projectId())
            + upload.kind().name().toLowerCase(java.util.Locale.ROOT)
            + "/"
            + UUID.randomUUID()
            + "/"
            + safeName(upload.name());
    try {
      storage.put(key, upload.content(), upload.contentType());
      Artifact saved =
          repository.save(
              new Artifact(
                  upload.projectId(),
                  upload.environmentId(),
                  upload.deploymentId(),
                  upload.kind(),
                  upload.name(),
                  key,
                  upload.contentType(),
                  upload.content().length,
                  sha256(upload.content()),
                  upload.createdBy()));
      return Optional.of(ArtifactResponse.from(saved));
    } catch (RuntimeException e) {
      log.warn(
          "Could not store artifact {} of project {}: {}",
          upload.name(),
          upload.projectId(),
          e.getMessage());
      return Optional.empty();
    }
  }

  @Transactional(readOnly = true)
  public PageResponse<ArtifactResponse> list(
      UUID projectId, UUID userId, ArtifactKind kind, UUID deploymentId, Pageable pageable) {
    projectAccess.requirePermission(projectId, userId, Permission.DEPLOYMENT_READ);
    return PageResponse.of(
        repository.search(projectId, kind, deploymentId, pageable), ArtifactResponse::from);
  }

  /** The artifact's metadata and content; the caller closes the stream. */
  public ArtifactDownload open(UUID artifactId, UUID userId) {
    Artifact artifact =
        repository
            .findById(artifactId)
            .orElseThrow(() -> new ResourceNotFoundException("Artifact", artifactId));
    try {
      projectAccess.requirePermission(artifact.getProjectId(), userId, Permission.DEPLOYMENT_READ);
    } catch (ResourceNotFoundException e) {
      throw new ResourceNotFoundException("Artifact", artifactId);
    }
    ObjectStorage storage = storageProvider.getIfAvailable();
    if (storage == null) {
      throw new ExternalServiceException(STORAGE, "Artifact storage is not configured");
    }
    try {
      return new ArtifactDownload(
          ArtifactResponse.from(artifact), storage.open(artifact.getStorageKey()));
    } catch (RuntimeException e) {
      throw new ExternalServiceException(STORAGE, "Could not read the artifact", e);
    }
  }

  /** Removes the objects of a deleted project; its rows were deleted with the project. */
  @TransactionalEventListener(fallbackExecution = true)
  public void onProjectDeleted(ProjectDeletedEvent event) {
    ObjectStorage storage = storageProvider.getIfAvailable();
    if (storage == null) {
      return;
    }
    try {
      storage.deletePrefix(projectPrefix(event.projectId()));
    } catch (RuntimeException e) {
      log.warn("Could not delete artifacts of project {}: {}", event.projectId(), e.getMessage());
    }
  }

  private static String projectPrefix(UUID projectId) {
    return "projects/" + projectId + "/";
  }

  static String safeName(String name) {
    String base = name.substring(name.lastIndexOf('/') + 1);
    String cleaned = base.replaceAll("[^A-Za-z0-9._-]", "_");
    return cleaned.isBlank() || cleaned.chars().allMatch(c -> c == '.') ? "artifact" : cleaned;
  }

  private static String sha256(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Metadata plus an open content stream. */
  public record ArtifactDownload(ArtifactResponse artifact, InputStream content) {}
}
