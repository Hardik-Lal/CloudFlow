package com.cloudflow.user.domain;

import com.cloudflow.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** A CloudFlow user. Created on first GitHub sign-in and refreshed on every login. */
@Entity
@Table(name = "users")
public class User extends BaseEntity {

  @Column(nullable = false, unique = true, updatable = false)
  private long githubId;

  @Column(nullable = false, length = 100)
  private String username;

  @Column(length = 320)
  private String email;

  @Column(length = 255)
  private String displayName;

  @Column(length = 1024)
  private String avatarUrl;

  private Instant lastLoginAt;

  protected User() {}

  public User(long githubId, String username) {
    this.githubId = githubId;
    this.username = username;
  }

  /** Copies the latest GitHub profile data; GitHub users can rename themselves at any time. */
  public void updateProfile(String username, String email, String displayName, String avatarUrl) {
    this.username = username;
    this.email = email;
    this.displayName = displayName;
    this.avatarUrl = avatarUrl;
  }

  public void recordLogin(Instant at) {
    this.lastLoginAt = at;
  }

  public long getGithubId() {
    return githubId;
  }

  public String getUsername() {
    return username;
  }

  public String getEmail() {
    return email;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getAvatarUrl() {
    return avatarUrl;
  }

  public Instant getLastLoginAt() {
    return lastLoginAt;
  }
}
