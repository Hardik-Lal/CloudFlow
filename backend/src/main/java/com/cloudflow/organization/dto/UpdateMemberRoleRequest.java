package com.cloudflow.organization.dto;

import com.cloudflow.organization.domain.Role;
import jakarta.validation.constraints.NotNull;

public record UpdateMemberRoleRequest(@NotNull Role role) {}
