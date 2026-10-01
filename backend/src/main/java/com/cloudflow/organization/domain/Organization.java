package com.cloudflow.organization.domain;

import com.cloudflow.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/** Tenant boundary: owns projects and memberships. The slug is immutable once created. */
@Entity
@Table(name = "organizations")
public class Organization extends BaseEntity {

  @Column(nullable = false, length = 100)
  private String name;

  @Column(nullable = false, unique = true, updatable = false, length = 50)
  private String slug;

  @Column(updatable = false)
  private UUID createdBy;

  protected Organization() {}

  public Organization(String name, String slug, UUID createdBy) {
    this.name = name;
    this.slug = slug;
    this.createdBy = createdBy;
  }

  public void rename(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public String getSlug() {
    return slug;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }
}
