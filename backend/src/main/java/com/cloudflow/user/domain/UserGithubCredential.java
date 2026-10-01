package com.cloudflow.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** The user's GitHub OAuth access token, stored encrypted, used to call the GitHub API. */
@Entity
@Table(name = "user_github_credentials")
public class UserGithubCredential {

  @Id private UUID userId;

  @Column(nullable = false)
  private String accessTokenEncrypted;

  @Column(nullable = false, length = 500)
  private String scopes;

  @CreationTimestamp
  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(nullable = false)
  private Instant updatedAt;

  protected UserGithubCredential() {}

  public UserGithubCredential(UUID userId, String accessTokenEncrypted, String scopes) {
    this.userId = userId;
    this.accessTokenEncrypted = accessTokenEncrypted;
    this.scopes = scopes;
  }

  public void replaceToken(String accessTokenEncrypted, String scopes) {
    this.accessTokenEncrypted = accessTokenEncrypted;
    this.scopes = scopes;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getAccessTokenEncrypted() {
    return accessTokenEncrypted;
  }

  public String getScopes() {
    return scopes;
  }
}
