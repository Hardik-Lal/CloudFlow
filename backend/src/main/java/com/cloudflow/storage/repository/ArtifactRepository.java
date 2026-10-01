package com.cloudflow.storage.repository;

import com.cloudflow.storage.domain.Artifact;
import com.cloudflow.storage.domain.ArtifactKind;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ArtifactRepository extends JpaRepository<Artifact, UUID> {

  @Query(
      "select a from Artifact a where a.projectId = :projectId"
          + " and (:kind is null or a.kind = :kind)"
          + " and (:deploymentId is null or a.deploymentId = :deploymentId)")
  Page<Artifact> search(UUID projectId, ArtifactKind kind, UUID deploymentId, Pageable pageable);
}
