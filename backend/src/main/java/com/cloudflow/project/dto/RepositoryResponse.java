package com.cloudflow.project.dto;

import com.cloudflow.project.domain.SourceRepository;
import java.time.Instant;

public record RepositoryResponse(
    long githubRepoId,
    String owner,
    String name,
    String fullName,
    String htmlUrl,
    String defaultBranch,
    boolean isPrivate,
    String language,
    Instant lastSyncedAt) {

  public static RepositoryResponse from(SourceRepository repository) {
    return new RepositoryResponse(
        repository.getGithubRepoId(),
        repository.getOwner(),
        repository.getName(),
        repository.getFullName(),
        repository.getHtmlUrl(),
        repository.getDefaultBranch(),
        repository.isPrivate(),
        repository.getLanguage(),
        repository.getLastSyncedAt());
  }
}
