package com.cloudflow.github.dto;

import com.cloudflow.github.client.GithubModels.Commit;
import java.time.Instant;

public record CommitResponse(
    String sha,
    String shortSha,
    String message,
    String authorName,
    String authorLogin,
    String authorAvatarUrl,
    Instant committedAt,
    String htmlUrl) {

  public static CommitResponse from(Commit commit) {
    String message = commit.commit().message();
    return new CommitResponse(
        commit.sha(),
        commit.sha().substring(0, Math.min(7, commit.sha().length())),
        // Only the subject line; full messages can be very long.
        message == null ? "" : message.lines().findFirst().orElse(""),
        commit.commit().author() == null ? null : commit.commit().author().name(),
        commit.author() == null ? null : commit.author().login(),
        commit.author() == null ? null : commit.author().avatarUrl(),
        commit.commit().author() == null ? null : commit.commit().author().date(),
        commit.htmlUrl());
  }
}
