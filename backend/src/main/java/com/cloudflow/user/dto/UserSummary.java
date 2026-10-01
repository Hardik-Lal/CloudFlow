package com.cloudflow.user.dto;

import com.cloudflow.user.domain.User;
import java.util.UUID;

/** Public, non-sensitive user fields shared with other modules (e.g. member lists). */
public record UserSummary(UUID id, String username, String displayName, String avatarUrl) {

  public static UserSummary from(User user) {
    return new UserSummary(
        user.getId(), user.getUsername(), user.getDisplayName(), user.getAvatarUrl());
  }
}
