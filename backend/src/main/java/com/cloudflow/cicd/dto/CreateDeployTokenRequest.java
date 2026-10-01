package com.cloudflow.cicd.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDeployTokenRequest(@NotBlank @Size(max = 100) String name) {}
