package com.cloudflow.project.domain;

import com.cloudflow.common.persistence.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A CloudFlow project: the aggregate root for its linked GitHub repository and branches. The slug
 * is immutable because it is used in image names and URLs.
 */
@Entity
@Table(name = "projects")
public class Project extends BaseEntity {

  @Column(nullable = false, updatable = false)
  private UUID organizationId;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(nullable = false, updatable = false, length = 50)
  private String slug;

  @Column(length = 500)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AppType appType;

  @Column(updatable = false)
  private UUID createdBy;

  @OneToOne(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
  private SourceRepository repository;

  protected Project() {}

  // The repository must reference its owning project (bidirectional one-to-one); the reference is
  // only stored, never used during construction.
  @SuppressWarnings("this-escape")
  public Project(
      UUID organizationId,
      String name,
      String slug,
      String description,
      UUID createdBy,
      RepositoryMetadata repositoryMetadata,
      AppType appType,
      Instant syncedAt) {
    this.organizationId = organizationId;
    this.name = name;
    this.slug = slug;
    this.description = description;
    this.createdBy = createdBy;
    this.appType = appType;
    this.repository = new SourceRepository(this, repositoryMetadata, syncedAt);
  }

  public void updateDetails(String name, String description) {
    this.name = name;
    this.description = description;
  }

  public void changeAppType(AppType appType) {
    this.appType = appType;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public String getName() {
    return name;
  }

  public String getSlug() {
    return slug;
  }

  public String getDescription() {
    return description;
  }

  public AppType getAppType() {
    return appType;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public SourceRepository getRepository() {
    return repository;
  }
}
