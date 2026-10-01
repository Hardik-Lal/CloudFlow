package com.cloudflow.assistant.dto;

public record ReindexResponse(int collected, int indexed, int unchanged, int removed) {}
