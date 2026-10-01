package com.cloudflow.project.dto;

import com.cloudflow.organization.domain.Permission;
import com.cloudflow.organization.domain.Role;
import com.cloudflow.project.domain.AppType;
import com.cloudflow.project.domain.Project;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * @param role the caller's role in the project's organization
 * @param permissions the caller's effective permissions (UI hint; enforced server-side)
 */
public record ProjectResponse(
    UUID id,
    UUID organizationId,
    String name,
    String slug,
    String description,
    AppType appType,
    RepositoryResponse repository,
    Role role,
    Set<Permission> permissions,
    Instant createdAt,
    Instant updatedAt) {

  public static ProjectResponse from(Project project, Role role) {
    return new ProjectResponse(
        project.getId(),
        project.getOrganizationId(),
        project.getName(),
        project.getSlug(),
        project.getDescription(),
        project.getAppType(),
        RepositoryResponse.from(project.getRepository()),
        role,
        role.permissions(),
        project.getCreatedAt(),
        project.getUpdatedAt());
  }
}
