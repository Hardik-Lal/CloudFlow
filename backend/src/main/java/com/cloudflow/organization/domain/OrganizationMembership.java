package com.cloudflow.organization.domain;

import com.cloudflow.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

/** A user's membership in an organization and the role it grants. */
@Entity
@Table(name = "organization_memberships")
public class OrganizationMembership extends BaseEntity {

  @Column(nullable = false, updatable = false)
  private UUID organizationId;

  @Column(nullable = false, updatable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Role role;

  protected OrganizationMembership() {}

  public OrganizationMembership(UUID organizationId, UUID userId, Role role) {
    this.organizationId = organizationId;
    this.userId = userId;
    this.role = role;
  }

  public void changeRole(Role role) {
    this.role = role;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public UUID getUserId() {
    return userId;
  }

  public Role getRole() {
    return role;
  }
}
