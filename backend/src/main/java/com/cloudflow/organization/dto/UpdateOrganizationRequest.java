package com.cloudflow.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateOrganizationRequest(@NotBlank @Size(max = 100) String name) {}
