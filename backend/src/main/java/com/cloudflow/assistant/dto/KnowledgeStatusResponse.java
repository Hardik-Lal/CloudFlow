package com.cloudflow.assistant.dto;

/**
 * @param enabled whether AI features are configured on this CloudFlow instance
 */
public record KnowledgeStatusResponse(
    boolean enabled, int documents, int chunks, String lastIndexedAt) {}
