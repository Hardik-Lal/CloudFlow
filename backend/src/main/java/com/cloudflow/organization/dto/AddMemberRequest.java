package com.cloudflow.organization.dto;

import com.cloudflow.organization.domain.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param username GitHub username of a user who has signed in to CloudFlow at least once
 */
public record AddMemberRequest(@NotBlank @Size(max = 100) String username, @NotNull Role role) {}
