package com.cloudflow.environment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateEnvironmentRequest(@NotBlank @Size(max = 255) String branch) {}
