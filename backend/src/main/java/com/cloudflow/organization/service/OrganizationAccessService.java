package com.cloudflow.organization.service;

import com.cloudflow.common.exception.ResourceNotFoundException;
import com.cloudflow.organization.domain.Permission;
import com.cloudflow.organization.domain.Role;
import com.cloudflow.organization.repository.OrganizationMembershipRepository;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single authorization point for organization-scoped resources. Every module checks access to
 * organization data through this service.
 */
@Service
public class OrganizationAccessService {

  private final OrganizationMembershipRepository membershipRepository;

  public OrganizationAccessService(OrganizationMembershipRepository membershipRepository) {
    this.membershipRepository = membershipRepository;
  }

  /**
   * Ensures the user holds {@code permission} in the organization and returns their role.
   *
   * @throws ResourceNotFoundException if the user is not a member (the organization's existence is
   *     not revealed)
   * @throws AccessDeniedException if the user is a member but lacks the permission
   */
  @Transactional(readOnly = true)
  public Role requirePermission(UUID organizationId, UUID userId, Permission permission) {
    Role role =
        membershipRepository
            .findRole(organizationId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Organization", organizationId));
    if (!role.has(permission)) {
      throw new AccessDeniedException(
          "Your role (" + role + ") does not allow this action (" + permission + ")");
    }
    return role;
  }
}
