package com.cloudflow.project.domain;

/** Repository attributes as reported by GitHub. */
public record RepositoryMetadata(
    long githubRepoId,
    String owner,
    String name,
    String fullName,
    String htmlUrl,
    String cloneUrl,
    String defaultBranch,
    boolean isPrivate,
    String language) {}
