package com.cloudflow.project.dto;

import com.cloudflow.project.domain.AppType;
import java.util.Set;
import java.util.UUID;

/** Read-only project facts other modules need (configuration validation, deployments). */
public record ProjectSnapshot(
    UUID id,
    UUID organizationId,
    String name,
    String slug,
    AppType appType,
    String repositoryOwner,
    String repositoryName,
    String repositoryFullName,
    String cloneUrl,
    String defaultBranch,
    Set<String> branchNames) {}
