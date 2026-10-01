package com.cloudflow.project.dto;

import com.cloudflow.organization.domain.Role;
import java.util.UUID;

/** A project the caller is authorized for, as seen by other modules. */
public record ProjectRef(
    UUID id,
    UUID organizationId,
    String slug,
    String repositoryFullName,
    String defaultBranch,
    Role role) {}
