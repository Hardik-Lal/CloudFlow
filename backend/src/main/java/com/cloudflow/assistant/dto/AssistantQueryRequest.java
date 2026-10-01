package com.cloudflow.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * @param environmentId optional; focuses retrieval and live context on one environment
 */
public record AssistantQueryRequest(
    @NotBlank @Size(min = 3, max = 2000) String question, UUID environmentId) {}
