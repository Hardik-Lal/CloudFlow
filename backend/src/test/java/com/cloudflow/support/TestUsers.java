package com.cloudflow.support;

import com.cloudflow.auth.service.AccessTokenService;
import com.cloudflow.auth.service.GithubLoginService;
import com.cloudflow.user.dto.GithubUserProfile;
import com.cloudflow.user.dto.UserResponse;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.boot.test.context.TestComponent;

/** Creates users the same way a GitHub sign-in does and mints access tokens for them. */
@TestComponent
public class TestUsers {

  private final GithubLoginService githubLoginService;
  private final AccessTokenService accessTokenService;

  public TestUsers(GithubLoginService githubLoginService, AccessTokenService accessTokenService) {
    this.githubLoginService = githubLoginService;
    this.accessTokenService = accessTokenService;
  }

  public TestUser create(String usernamePrefix) {
    long githubId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    String username = usernamePrefix + "-" + Long.toString(githubId, 36);
    UserResponse user =
        githubLoginService.completeLogin(
            new GithubUserProfile(githubId, username, "Test " + usernamePrefix, null, null),
            "gho_test_token_" + githubId,
            List.of("read:user", "repo"));
    String token = accessTokenService.issue(user.id(), user.username()).value();
    return new TestUser(user, "Bearer " + token);
  }

  public record TestUser(UserResponse profile, String authorization) {

    public UUID id() {
      return profile.id();
    }

    public String username() {
      return profile.username();
    }
  }
}
