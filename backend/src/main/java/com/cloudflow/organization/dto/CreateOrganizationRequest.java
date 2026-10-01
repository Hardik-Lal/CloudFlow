package com.cloudflow.organization.dto;

import com.cloudflow.common.util.Slugs;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param slug optional; derived from {@code name} when omitted
 */
public record CreateOrganizationRequest(
    @NotBlank @Size(max = 100) String name,
    @Size(min = 3, max = 50) @Pattern(regexp = Slugs.PATTERN) String slug) {}
