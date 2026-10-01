package com.cloudflow.auth.oauth;

import com.cloudflow.auth.service.RefreshTokenService;
import com.cloudflow.auth.web.RefreshTokenCookies;
import com.cloudflow.common.config.WebProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * Ends the OAuth dance: issues a refresh-token cookie, discards the temporary login session, and
 * redirects to the frontend, which then exchanges the cookie for an access token.
 */
@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

  private final RefreshTokenService refreshTokenService;
  private final RefreshTokenCookies refreshTokenCookies;
  private final WebProperties webProperties;

  public OAuth2LoginSuccessHandler(
      RefreshTokenService refreshTokenService,
      RefreshTokenCookies refreshTokenCookies,
      WebProperties webProperties) {
    this.refreshTokenService = refreshTokenService;
    this.refreshTokenCookies = refreshTokenCookies;
    this.webProperties = webProperties;
  }

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException {
    OAuth2User principal = (OAuth2User) authentication.getPrincipal();
    String userId = principal.getAttribute(GithubOAuth2UserService.CLOUDFLOW_USER_ID);

    refreshTokenCookies.write(response, refreshTokenService.issue(UUID.fromString(userId)));

    // The HTTP session only existed to hold OAuth state during the redirect round trip.
    SecurityContextHolder.clearContext();
    HttpSession session = request.getSession(false);
    if (session != null) {
      session.invalidate();
    }
    response.sendRedirect(webProperties.frontendUrl() + "/auth/callback");
  }
}
