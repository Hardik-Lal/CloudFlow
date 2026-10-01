package com.cloudflow.github.dto;

import com.cloudflow.github.client.GithubModels.PullRequest;
import java.time.Instant;

public record PullRequestResponse(
    long number,
    String title,
    String state,
    boolean draft,
    String authorLogin,
    String authorAvatarUrl,
    String headBranch,
    String baseBranch,
    String htmlUrl,
    Instant createdAt,
    Instant updatedAt) {

  public static PullRequestResponse from(PullRequest pullRequest) {
    return new PullRequestResponse(
        pullRequest.number(),
        pullRequest.title(),
        pullRequest.state(),
        pullRequest.draft(),
        pullRequest.user() == null ? null : pullRequest.user().login(),
        pullRequest.user() == null ? null : pullRequest.user().avatarUrl(),
        pullRequest.head().ref(),
        pullRequest.base().ref(),
        pullRequest.htmlUrl(),
        pullRequest.createdAt(),
        pullRequest.updatedAt());
  }
}
