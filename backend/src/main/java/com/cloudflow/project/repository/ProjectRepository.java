package com.cloudflow.project.repository;

import com.cloudflow.project.domain.Project;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

  @EntityGraph(attributePaths = "repository")
  List<Project> findAllByOrganizationIdOrderByNameAsc(UUID organizationId);

  boolean existsByOrganizationIdAndSlug(UUID organizationId, String slug);

  @Query(
      "select p.id from Project p where lower(p.repository.fullName) = lower(:repositoryFullName)")
  List<UUID> findIdsByRepositoryFullName(String repositoryFullName);

  @Query(
      "select count(p) > 0 from Project p"
          + " where p.organizationId = :organizationId and p.repository.githubRepoId = :githubRepoId")
  boolean existsByOrganizationIdAndGithubRepoId(UUID organizationId, long githubRepoId);
}
