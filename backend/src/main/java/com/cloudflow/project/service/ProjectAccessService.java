package com.cloudflow.project.service;

import com.cloudflow.common.exception.ResourceNotFoundException;
import com.cloudflow.organization.domain.Permission;
import com.cloudflow.organization.domain.Role;
import com.cloudflow.organization.service.OrganizationAccessService;
import com.cloudflow.project.domain.Project;
import com.cloudflow.project.dto.ProjectRef;
import com.cloudflow.project.repository.ProjectRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Authorizes access to project-scoped resources through the owning organization's RBAC. */
@Service
public class ProjectAccessService {

  private final ProjectRepository projectRepository;
  private final OrganizationAccessService organizationAccessService;

  public ProjectAccessService(
      ProjectRepository projectRepository, OrganizationAccessService organizationAccessService) {
    this.projectRepository = projectRepository;
    this.organizationAccessService = organizationAccessService;
  }

  /**
   * @throws ResourceNotFoundException if the project does not exist or the user is not a member of
   *     its organization
   */
  @Transactional(readOnly = true)
  public ProjectRef requirePermission(UUID projectId, UUID userId, Permission permission) {
    AuthorizedProject authorized = loadAuthorized(projectId, userId, permission);
    Project project = authorized.project();
    return new ProjectRef(
        project.getId(),
        project.getOrganizationId(),
        project.getSlug(),
        project.getRepository().getFullName(),
        project.getRepository().getDefaultBranch(),
        authorized.role());
  }

  /** Loads the project and checks the permission; for use inside the project module. */
  AuthorizedProject loadAuthorized(UUID projectId, UUID userId, Permission permission) {
    Project project =
        projectRepository
            .findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
    Role role;
    try {
      role =
          organizationAccessService.requirePermission(
              project.getOrganizationId(), userId, permission);
    } catch (ResourceNotFoundException e) {
      // Non-members must not learn that the project exists.
      throw new ResourceNotFoundException("Project", projectId);
    }
    return new AuthorizedProject(project, role);
  }

  record AuthorizedProject(Project project, Role role) {}
}
