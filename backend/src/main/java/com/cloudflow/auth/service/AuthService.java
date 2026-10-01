package com.cloudflow.auth.service;

import com.cloudflow.auth.dto.AccessToken;
import com.cloudflow.auth.dto.AuthSession;
import com.cloudflow.auth.dto.AuthTokenResponse;
import com.cloudflow.auth.dto.RefreshTokenRotation;
import com.cloudflow.common.exception.UnauthorizedException;
import com.cloudflow.user.dto.UserResponse;
import com.cloudflow.user.service.UserService;
import org.springframework.stereotype.Service;

/** Session operations exposed through the auth API: refresh and logout. */
@Service
public class AuthService {

  private final RefreshTokenService refreshTokenService;
  private final AccessTokenService accessTokenService;
  private final UserService userService;

  public AuthService(
      RefreshTokenService refreshTokenService,
      AccessTokenService accessTokenService,
      UserService userService) {
    this.refreshTokenService = refreshTokenService;
    this.accessTokenService = accessTokenService;
    this.userService = userService;
  }

  public AuthSession refresh(String refreshToken) {
    if (refreshToken == null || refreshToken.isBlank()) {
      throw new UnauthorizedException("Missing refresh token");
    }
    RefreshTokenRotation rotation = refreshTokenService.rotate(refreshToken);
    UserResponse user = userService.getUser(rotation.userId());
    AccessToken accessToken = accessTokenService.issue(user.id(), user.username());
    return new AuthSession(AuthTokenResponse.bearer(accessToken, user), rotation.newRefreshToken());
  }

  public void logout(String refreshToken) {
    if (refreshToken != null && !refreshToken.isBlank()) {
      refreshTokenService.revoke(refreshToken);
    }
  }
}
