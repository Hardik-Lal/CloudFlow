package com.cloudflow.user.dto;

import com.cloudflow.user.domain.User;
import java.util.UUID;

public record UserResponse(
    UUID id, String username, String displayName, String email, String avatarUrl) {

  public static UserResponse from(User user) {
    return new UserResponse(
        user.getId(),
        user.getUsername(),
        user.getDisplayName(),
        user.getEmail(),
        user.getAvatarUrl());
  }
}
