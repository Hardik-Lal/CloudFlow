package com.cloudflow.project.dto;

import com.cloudflow.common.util.Slugs;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param repositoryFullName GitHub repository as {@code owner/name}
 * @param name optional; defaults to the repository name
 * @param slug optional; derived from the name when omitted
 */
public record CreateProjectRequest(
    @NotBlank @Pattern(regexp = "^[A-Za-z0-9_.-]{1,100}/[A-Za-z0-9_.-]{1,100}$")
        String repositoryFullName,
    @Size(max = 100) String name,
    @Size(min = 3, max = 50) @Pattern(regexp = Slugs.PATTERN) String slug,
    @Size(max = 500) String description) {}
