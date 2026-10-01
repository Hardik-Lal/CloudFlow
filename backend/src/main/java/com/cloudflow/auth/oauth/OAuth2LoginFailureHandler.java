package com.cloudflow.auth.oauth;

import com.cloudflow.common.config.WebProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/** Sends the user back to the frontend login page with an error flag when sign-in fails. */
@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

  private static final Logger log = LoggerFactory.getLogger(OAuth2LoginFailureHandler.class);

  private final WebProperties webProperties;

  public OAuth2LoginFailureHandler(WebProperties webProperties) {
    this.webProperties = webProperties;
  }

  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
      throws IOException {
    log.warn("GitHub sign-in failed: {}", exception.getMessage());
    response.sendRedirect(webProperties.frontendUrl() + "/login?error=oauth_failed");
  }
}
