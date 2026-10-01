package com.cloudflow.auth.dto;

import com.cloudflow.user.dto.UserResponse;

public record AuthTokenResponse(
    String accessToken, String tokenType, long expiresIn, UserResponse user) {

  public static AuthTokenResponse bearer(AccessToken token, UserResponse user) {
    return new AuthTokenResponse(token.value(), "Bearer", token.expiresInSeconds(), user);
  }
}
