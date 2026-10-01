package com.cloudflow.project.dto;

import com.cloudflow.project.domain.AppType;
import com.cloudflow.project.domain.Project;
import java.time.Instant;
import java.util.UUID;

public record ProjectSummaryResponse(
    UUID id,
    UUID organizationId,
    String name,
    String slug,
    String description,
    AppType appType,
    RepositoryResponse repository,
    Instant updatedAt) {

  public static ProjectSummaryResponse from(Project project) {
    return new ProjectSummaryResponse(
        project.getId(),
        project.getOrganizationId(),
        project.getName(),
        project.getSlug(),
        project.getDescription(),
        project.getAppType(),
        RepositoryResponse.from(project.getRepository()),
        project.getUpdatedAt());
  }
}
