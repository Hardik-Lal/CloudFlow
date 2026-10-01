package com.cloudflow.organization.dto;

import com.cloudflow.organization.domain.Organization;
import com.cloudflow.organization.domain.Permission;
import com.cloudflow.organization.domain.Role;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * @param role the caller's role in the organization
 * @param permissions the caller's effective permissions, so clients can adapt the UI without
 *     duplicating the RBAC matrix (the server still enforces every check)
 */
public record OrganizationResponse(
    UUID id, String name, String slug, Role role, Set<Permission> permissions, Instant createdAt) {

  public static OrganizationResponse from(Organization organization, Role role) {
    return new OrganizationResponse(
        organization.getId(),
        organization.getName(),
        organization.getSlug(),
        role,
        role.permissions(),
        organization.getCreatedAt());
  }
}
