package com.cloudflow.user.dto;

/** Profile attributes returned by GitHub's {@code /user} endpoint during sign-in. */
public record GithubUserProfile(
    long githubId, String login, String name, String email, String avatarUrl) {}
