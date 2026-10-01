package com.cloudflow.project.service;

import com.cloudflow.common.exception.ResourceNotFoundException;
import com.cloudflow.project.domain.Branch;
import com.cloudflow.project.domain.Project;
import com.cloudflow.project.domain.SourceRepository;
import com.cloudflow.project.dto.ProjectSnapshot;
import com.cloudflow.project.repository.ProjectRepository;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Internal, unauthenticated project lookups for other modules. Callers must already have authorized
 * the user (e.g. through {@link ProjectAccessService}).
 */
@Service
public class ProjectLookupService {

  private final ProjectRepository projectRepository;

  public ProjectLookupService(ProjectRepository projectRepository) {
    this.projectRepository = projectRepository;
  }

  /** Projects linked to a GitHub repository (one per organization at most). */
  @Transactional(readOnly = true)
  public List<UUID> findIdsByRepositoryFullName(String repositoryFullName) {
    return projectRepository.findIdsByRepositoryFullName(repositoryFullName);
  }

  @Transactional(readOnly = true)
  public ProjectSnapshot snapshot(UUID projectId) {
    Project project =
        projectRepository
            .findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
    SourceRepository repository = project.getRepository();
    return new ProjectSnapshot(
        project.getId(),
        project.getOrganizationId(),
        project.getName(),
        project.getSlug(),
        project.getAppType(),
        repository.getOwner(),
        repository.getName(),
        repository.getFullName(),
        repository.getCloneUrl(),
        repository.getDefaultBranch(),
        repository.getBranches().stream().map(Branch::getName).collect(Collectors.toSet()));
  }
}
