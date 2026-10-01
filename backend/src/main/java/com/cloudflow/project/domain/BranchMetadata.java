package com.cloudflow.project.domain;

/** Branch attributes as reported by GitHub. */
public record BranchMetadata(String name, String headCommitSha, boolean isProtected) {}
