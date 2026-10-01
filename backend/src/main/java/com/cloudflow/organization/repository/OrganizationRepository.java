package com.cloudflow.organization.repository;

import com.cloudflow.organization.domain.Organization;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

  boolean existsBySlug(String slug);

  /**
   * Locks the organization row. Membership changes take this lock so that concurrent requests
   * cannot remove or demote the last Owner.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from Organization o where o.id = :id")
  Optional<Organization> findByIdForUpdate(UUID id);
}
