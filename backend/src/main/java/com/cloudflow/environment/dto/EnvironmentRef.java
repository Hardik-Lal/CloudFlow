package com.cloudflow.environment.dto;

import com.cloudflow.environment.domain.EnvironmentType;
import com.cloudflow.organization.domain.Role;
import java.util.UUID;

/** An environment the caller is authorized for, as seen by other modules. */
public record EnvironmentRef(
    UUID id, UUID projectId, UUID organizationId, EnvironmentType type, String branch, Role role) {}
