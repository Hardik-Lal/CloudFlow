package com.cloudflow.github.dto;

import com.cloudflow.github.client.GithubModels.Repository;
import java.time.Instant;

public record GithubRepositoryResponse(
    long id,
    String owner,
    String name,
    String fullName,
    String description,
    String htmlUrl,
    String defaultBranch,
    boolean isPrivate,
    String language,
    Instant pushedAt) {

  public static GithubRepositoryResponse from(Repository repository) {
    return new GithubRepositoryResponse(
        repository.id(),
        repository.owner().login(),
        repository.name(),
        repository.fullName(),
        repository.description(),
        repository.htmlUrl(),
        repository.defaultBranch(),
        repository.isPrivate(),
        repository.language(),
        repository.pushedAt());
  }
}
