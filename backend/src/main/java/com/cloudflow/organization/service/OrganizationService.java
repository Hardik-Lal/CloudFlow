package com.cloudflow.organization.service;

import com.cloudflow.audit.domain.AuditAction;
import com.cloudflow.audit.service.AuditLogger;
import com.cloudflow.common.exception.ConflictException;
import com.cloudflow.common.exception.ResourceNotFoundException;
import com.cloudflow.common.util.Slugs;
import com.cloudflow.organization.domain.Organization;
import com.cloudflow.organization.domain.OrganizationMembership;
import com.cloudflow.organization.domain.Permission;
import com.cloudflow.organization.domain.Role;
import com.cloudflow.organization.dto.CreateOrganizationRequest;
import com.cloudflow.organization.dto.OrganizationResponse;
import com.cloudflow.organization.dto.UpdateOrganizationRequest;
import com.cloudflow.organization.repository.OrganizationMembershipRepository;
import com.cloudflow.organization.repository.OrganizationRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {

  private final AuditLogger audit;

  private final OrganizationRepository organizationRepository;
  private final OrganizationMembershipRepository membershipRepository;
  private final OrganizationAccessService accessService;

  public OrganizationService(
      OrganizationRepository organizationRepository,
      OrganizationMembershipRepository membershipRepository,
      OrganizationAccessService accessService,
      AuditLogger audit) {
    this.audit = audit;
    this.organizationRepository = organizationRepository;
    this.membershipRepository = membershipRepository;
    this.accessService = accessService;
  }

  /** Creates an organization and makes the creator its Owner. */
  @Transactional
  public OrganizationResponse create(UUID userId, CreateOrganizationRequest request) {
    String name = request.name().strip();
    String slug = Slugs.requestedOrDerived(request.slug(), name);
    if (organizationRepository.existsBySlug(slug)) {
      throw new ConflictException("An organization with slug '" + slug + "' already exists");
    }

    Organization organization = organizationRepository.save(new Organization(name, slug, userId));
    membershipRepository.save(new OrganizationMembership(organization.getId(), userId, Role.OWNER));
    audit.record(
        organization.getId(),
        userId,
        AuditAction.ORGANIZATION_CREATED,
        "organization",
        organization.getId(),
        Map.of("name", name, "slug", slug));
    return OrganizationResponse.from(organization, Role.OWNER);
  }

  @Transactional(readOnly = true)
  public List<OrganizationResponse> listForUser(UUID userId) {
    Map<UUID, Role> roles =
        membershipRepository.findAllByUserId(userId).stream()
            .collect(
                Collectors.toMap(
                    OrganizationMembership::getOrganizationId, OrganizationMembership::getRole));
    return organizationRepository.findAllById(roles.keySet()).stream()
        .sorted(Comparator.comparing(Organization::getName, String.CASE_INSENSITIVE_ORDER))
        .map(
            organization ->
                OrganizationResponse.from(organization, roles.get(organization.getId())))
        .toList();
  }

  @Transactional(readOnly = true)
  public OrganizationResponse get(UUID organizationId, UUID userId) {
    Role role = accessService.requirePermission(organizationId, userId, Permission.ORG_READ);
    return OrganizationResponse.from(load(organizationId), role);
  }

  @Transactional
  public OrganizationResponse update(
      UUID organizationId, UUID userId, UpdateOrganizationRequest request) {
    Role role = accessService.requirePermission(organizationId, userId, Permission.ORG_UPDATE);
    Organization organization = load(organizationId);
    String previousName = organization.getName();
    organization.rename(request.name().strip());
    audit.record(
        organizationId,
        userId,
        AuditAction.ORGANIZATION_UPDATED,
        "organization",
        organizationId,
        Map.of("from", previousName, "to", organization.getName()));
    return OrganizationResponse.from(organization, role);
  }

  @Transactional
  public void delete(UUID organizationId, UUID userId) {
    accessService.requirePermission(organizationId, userId, Permission.ORG_DELETE);
    Organization organization = load(organizationId);
    organizationRepository.delete(organization);
    // Recorded without the organization id: its audit trail is deleted with it, this entry stays.
    audit.record(
        null,
        userId,
        AuditAction.ORGANIZATION_DELETED,
        "organization",
        organizationId,
        Map.of("name", organization.getName(), "slug", organization.getSlug()));
  }

  private Organization load(UUID organizationId) {
    return organizationRepository
        .findById(organizationId)
        .orElseThrow(() -> new ResourceNotFoundException("Organization", organizationId));
  }
}
