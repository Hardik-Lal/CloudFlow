package com.cloudflow.auth.web;

import com.cloudflow.auth.dto.AuthSession;
import com.cloudflow.auth.dto.AuthTokenResponse;
import com.cloudflow.auth.service.AuthService;
import com.cloudflow.common.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthService authService;
  private final RefreshTokenCookies refreshTokenCookies;

  public AuthController(AuthService authService, RefreshTokenCookies refreshTokenCookies) {
    this.authService = authService;
    this.refreshTokenCookies = refreshTokenCookies;
  }

  @PostMapping("/refresh")
  public ResponseEntity<AuthTokenResponse> refresh(
      HttpServletRequest request, HttpServletResponse response) {
    try {
      AuthSession session = authService.refresh(refreshTokenCookies.read(request));
      refreshTokenCookies.write(response, session.refreshToken());
      return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(session.response());
    } catch (UnauthorizedException e) {
      refreshTokenCookies.clear(response);
      throw e;
    }
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
    authService.logout(refreshTokenCookies.read(request));
    refreshTokenCookies.clear(response);
    return ResponseEntity.noContent().build();
  }
}
