package com.cloudflow.organization.service;

import com.cloudflow.audit.domain.AuditAction;
import com.cloudflow.audit.service.AuditLogger;
import com.cloudflow.common.exception.ConflictException;
import com.cloudflow.common.exception.ResourceNotFoundException;
import com.cloudflow.organization.domain.OrganizationMembership;
import com.cloudflow.organization.domain.Permission;
import com.cloudflow.organization.domain.Role;
import com.cloudflow.organization.dto.AddMemberRequest;
import com.cloudflow.organization.dto.MemberResponse;
import com.cloudflow.organization.dto.UpdateMemberRoleRequest;
import com.cloudflow.organization.repository.OrganizationMembershipRepository;
import com.cloudflow.organization.repository.OrganizationRepository;
import com.cloudflow.user.dto.UserSummary;
import com.cloudflow.user.service.UserService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages organization members. Rules:
 *
 * <ul>
 *   <li>Owners manage everyone; Admins manage only Developers and Viewers and cannot grant Owner.
 *   <li>Any member may leave the organization.
 *   <li>An organization always keeps at least one Owner.
 * </ul>
 */
@Service
public class MembershipService {

  private final AuditLogger audit;

  private final OrganizationRepository organizationRepository;
  private final OrganizationMembershipRepository membershipRepository;
  private final OrganizationAccessService accessService;
  private final UserService userService;

  public MembershipService(
      OrganizationRepository organizationRepository,
      OrganizationMembershipRepository membershipRepository,
      OrganizationAccessService accessService,
      UserService userService,
      AuditLogger audit) {
    this.audit = audit;
    this.organizationRepository = organizationRepository;
    this.membershipRepository = membershipRepository;
    this.accessService = accessService;
    this.userService = userService;
  }

  @Transactional(readOnly = true)
  public List<MemberResponse> list(UUID organizationId, UUID actorId) {
    accessService.requirePermission(organizationId, actorId, Permission.MEMBER_READ);
    List<OrganizationMembership> memberships =
        membershipRepository.findAllByOrganizationIdOrderByCreatedAtAsc(organizationId);
    Map<UUID, UserSummary> users =
        userService.findSummaries(
            memberships.stream().map(OrganizationMembership::getUserId).toList());
    return memberships.stream()
        .filter(membership -> users.containsKey(membership.getUserId()))
        .map(membership -> MemberResponse.from(membership, users.get(membership.getUserId())))
        .toList();
  }

  @Transactional
  public MemberResponse add(UUID organizationId, UUID actorId, AddMemberRequest request) {
    Role actorRole =
        accessService.requirePermission(organizationId, actorId, Permission.MEMBER_MANAGE);
    ensureCanManage(actorRole, null, request.role());

    UserSummary user =
        userService
            .findByUsername(request.username().strip())
            .orElseThrow(() -> new ResourceNotFoundException("User", request.username()));
    if (membershipRepository.existsByOrganizationIdAndUserId(organizationId, user.id())) {
      throw new ConflictException(user.username() + " is already a member of this organization");
    }

    OrganizationMembership membership =
        membershipRepository.save(
            new OrganizationMembership(organizationId, user.id(), request.role()));
    audit.record(
        organizationId,
        actorId,
        AuditAction.MEMBER_ADDED,
        "member",
        user.id(),
        Map.of("username", user.username(), "role", request.role().name()));
    return MemberResponse.from(membership, user);
  }

  @Transactional
  public MemberResponse changeRole(
      UUID organizationId, UUID actorId, UUID memberId, UpdateMemberRoleRequest request) {
    Role actorRole =
        accessService.requirePermission(organizationId, actorId, Permission.MEMBER_MANAGE);
    lockOrganization(organizationId);
    OrganizationMembership membership = loadMembership(organizationId, memberId);

    ensureCanManage(actorRole, membership.getRole(), request.role());
    if (membership.getRole() == Role.OWNER && request.role() != Role.OWNER) {
      ensureAnotherOwnerRemains(organizationId);
    }

    Role previousRole = membership.getRole();
    membership.changeRole(request.role());
    UserSummary member = loadUser(memberId);
    audit.record(
        organizationId,
        actorId,
        AuditAction.MEMBER_ROLE_CHANGED,
        "member",
        memberId,
        Map.of(
            "username", member.username(),
            "from", previousRole.name(),
            "to", request.role().name()));
    return MemberResponse.from(membership, member);
  }

  /** Removes a member. Members may always remove themselves (leave), subject to the Owner rule. */
  @Transactional
  public void remove(UUID organizationId, UUID actorId, UUID memberId) {
    boolean leaving = actorId.equals(memberId);
    Role actorRole =
        leaving
            ? accessService.requirePermission(organizationId, actorId, Permission.ORG_READ)
            : accessService.requirePermission(organizationId, actorId, Permission.MEMBER_MANAGE);
    lockOrganization(organizationId);
    OrganizationMembership membership = loadMembership(organizationId, memberId);

    if (!leaving) {
      ensureCanManage(actorRole, membership.getRole(), null);
    }
    if (membership.getRole() == Role.OWNER) {
      ensureAnotherOwnerRemains(organizationId);
    }
    membershipRepository.delete(membership);
    audit.record(
        organizationId,
        actorId,
        AuditAction.MEMBER_REMOVED,
        "member",
        memberId,
        Map.of("role", membership.getRole().name(), "left", String.valueOf(leaving)));
  }

  private static void ensureCanManage(Role actorRole, Role currentRole, Role newRole) {
    if (!actorRole.canManage(currentRole, newRole)) {
      throw new AccessDeniedException(
          "Your role (" + actorRole + ") cannot manage this member or assign this role");
    }
  }

  private void ensureAnotherOwnerRemains(UUID organizationId) {
    if (membershipRepository.countByOrganizationIdAndRole(organizationId, Role.OWNER) <= 1) {
      throw new ConflictException(
          "An organization must keep at least one Owner; assign another Owner first");
    }
  }

  private void lockOrganization(UUID organizationId) {
    organizationRepository
        .findByIdForUpdate(organizationId)
        .orElseThrow(() -> new ResourceNotFoundException("Organization", organizationId));
  }

  private OrganizationMembership loadMembership(UUID organizationId, UUID memberId) {
    return membershipRepository
        .findByOrganizationIdAndUserId(organizationId, memberId)
        .orElseThrow(() -> new ResourceNotFoundException("Member", memberId));
  }

  private UserSummary loadUser(UUID userId) {
    UserSummary user = userService.findSummaries(List.of(userId)).get(userId);
    if (user == null) {
      throw new ResourceNotFoundException("User", userId);
    }
    return user;
  }
}
