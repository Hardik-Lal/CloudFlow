package com.cloudflow.auth.service;

import com.cloudflow.audit.domain.AuditAction;
import com.cloudflow.audit.service.AuditLogger;
import com.cloudflow.user.dto.GithubUserProfile;
import com.cloudflow.user.dto.UserResponse;
import com.cloudflow.user.service.GithubCredentialService;
import com.cloudflow.user.service.UserService;
import java.util.Collection;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Completes a GitHub sign-in: provisions the user and stores their GitHub token atomically. */
@Service
public class GithubLoginService {

  private final AuditLogger audit;

  private final UserService userService;
  private final GithubCredentialService githubCredentialService;

  public GithubLoginService(
      UserService userService, GithubCredentialService githubCredentialService, AuditLogger audit) {
    this.audit = audit;
    this.userService = userService;
    this.githubCredentialService = githubCredentialService;
  }

  @Transactional
  public UserResponse completeLogin(
      GithubUserProfile profile, String githubAccessToken, Collection<String> scopes) {
    UserResponse user = userService.recordGithubLogin(profile);
    githubCredentialService.storeToken(user.id(), githubAccessToken, scopes);
    audit.recordWithUsername(
        null,
        user.id(),
        user.username(),
        AuditAction.USER_SIGNED_IN,
        "user",
        user.id(),
        Map.of("provider", "github"));
    return user;
  }
}
