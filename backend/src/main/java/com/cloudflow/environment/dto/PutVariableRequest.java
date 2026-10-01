package com.cloudflow.environment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PutVariableRequest(@NotNull @Size(max = 32_768) String value, boolean secret) {}
