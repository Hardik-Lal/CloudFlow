package com.cloudflow.auth.web;

import com.cloudflow.auth.config.AuthProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

/**
 * Reads and writes the refresh-token cookie. The cookie is HttpOnly (not readable by JavaScript),
 * SameSite=Strict (not sent on cross-site requests, which prevents CSRF on the auth endpoints), and
 * scoped to the auth API path.
 */
@Component
public class RefreshTokenCookies {

  static final String COOKIE_PATH = "/api/v1/auth";

  private final AuthProperties properties;

  public RefreshTokenCookies(AuthProperties properties) {
    this.properties = properties;
  }

  public String read(HttpServletRequest request) {
    Cookie cookie = WebUtils.getCookie(request, properties.refreshCookieName());
    return cookie == null ? null : cookie.getValue();
  }

  public void write(HttpServletResponse response, String refreshToken) {
    addCookie(response, refreshToken, properties.refreshTokenTtl());
  }

  public void clear(HttpServletResponse response) {
    addCookie(response, "", Duration.ZERO);
  }

  private void addCookie(HttpServletResponse response, String value, Duration maxAge) {
    ResponseCookie cookie =
        ResponseCookie.from(properties.refreshCookieName(), value)
            .httpOnly(true)
            .secure(properties.refreshCookieSecure())
            .sameSite("Strict")
            .path(COOKIE_PATH)
            .maxAge(maxAge)
            .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }
}
