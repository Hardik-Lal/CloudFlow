package com.cloudflow.organization.repository;

import com.cloudflow.organization.domain.OrganizationMembership;
import com.cloudflow.organization.domain.Role;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrganizationMembershipRepository
    extends JpaRepository<OrganizationMembership, UUID> {

  Optional<OrganizationMembership> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);

  @Query(
      "select m.role from OrganizationMembership m"
          + " where m.organizationId = :organizationId and m.userId = :userId")
  Optional<Role> findRole(UUID organizationId, UUID userId);

  List<OrganizationMembership> findAllByOrganizationIdOrderByCreatedAtAsc(UUID organizationId);

  List<OrganizationMembership> findAllByUserId(UUID userId);

  boolean existsByOrganizationIdAndUserId(UUID organizationId, UUID userId);

  long countByOrganizationIdAndRole(UUID organizationId, Role role);
}
