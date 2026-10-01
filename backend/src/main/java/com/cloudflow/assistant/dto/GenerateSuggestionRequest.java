package com.cloudflow.assistant.dto;

import com.cloudflow.assistant.domain.SuggestionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * @param environmentId required for ENV_TEMPLATE; otherwise optional context
 */
public record GenerateSuggestionRequest(
    @NotNull SuggestionType type, UUID environmentId, @Size(max = 2000) String instructions) {}
