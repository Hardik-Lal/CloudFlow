package com.cloudflow.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.cloudflow.auth.service.RefreshTokenService;
import com.cloudflow.support.IntegrationTest;
import com.cloudflow.support.TestUsers;
import com.cloudflow.support.TestUsers.TestUser;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
class AuthIntegrationTest {

  private static final String COOKIE = "cloudflow_refresh";

  @Autowired private MockMvcTester mvc;
  @Autowired private TestUsers users;
  @Autowired private RefreshTokenService refreshTokenService;

  @Test
  void refreshExchangesCookieForAccessTokenAndRotatesCookie() throws Exception {
    TestUser user = users.create("refresh");
    String refreshToken = refreshTokenService.issue(user.id());

    MvcTestResult result = refresh(refreshToken);

    assertThat(result).hasStatusOk();
    assertThat(result).bodyJson().extractingPath("$.tokenType").isEqualTo("Bearer");
    assertThat(result).bodyJson().extractingPath("$.expiresIn").isEqualTo(900);
    assertThat(result).bodyJson().extractingPath("$.user.username").isEqualTo(user.username());
    assertThat(result.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");

    String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
    assertThat(setCookie)
        .contains("HttpOnly")
        .contains("SameSite=Strict")
        .contains("Path=/api/v1/auth")
        .doesNotContain(refreshToken);

    String accessToken = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    assertThat(
            mvc.get()
                .uri("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.id")
        .isEqualTo(user.id().toString());
  }

  @Test
  void aRotatedRefreshTokenCannotBeUsedAgain() {
    TestUser user = users.create("reuse");
    String original = refreshTokenService.issue(user.id());
    String rotated = rotatedCookie(refresh(original));

    assertThat(refresh(original)).hasStatus(HttpStatus.UNAUTHORIZED);
    // Immediate reuse looks like a concurrent refresh, so the new session stays valid
    // (reuse after the grace period revokes everything; see RefreshTokenServiceTest).
    assertThat(refresh(rotated)).hasStatusOk();
  }

  @Test
  void refreshWithoutCookieReturnsProblemAndClearsCookie() {
    MvcTestResult result = mvc.post().uri("/api/v1/auth/refresh").exchange();

    assertThat(result)
        .hasStatus(HttpStatus.UNAUTHORIZED)
        .bodyJson()
        .extractingPath("$.type")
        .isEqualTo("urn:cloudflow:problem:unauthorized");
    assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
  }

  @Test
  void refreshWithUnknownTokenIsRejected() {
    assertThat(refresh("not-a-real-token")).hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void logoutRevokesTheRefreshToken() {
    TestUser user = users.create("logout");
    String refreshToken = refreshTokenService.issue(user.id());

    assertThat(mvc.post().uri("/api/v1/auth/logout").cookie(new Cookie(COOKIE, refreshToken)))
        .hasStatus(HttpStatus.NO_CONTENT);
    assertThat(refresh(refreshToken)).hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void apiRejectsMissingOrInvalidBearerTokens() {
    assertThat(mvc.get().uri("/api/v1/users/me"))
        .hasStatus(HttpStatus.UNAUTHORIZED)
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo(401);

    TestUser user = users.create("tamper");
    String tampered = user.authorization().substring(0, user.authorization().length() - 2) + "xx";
    assertThat(mvc.get().uri("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, tampered))
        .hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void requestsRejectedByTheFirewallAreClientErrors() {
    assertThat(mvc.get().uri("/api/v1/users/me/..;/..;/actuator"))
        .hasStatus(HttpStatus.BAD_REQUEST);
  }

  @Test
  void githubLoginRedirectsToGithubWithRequiredScopes() {
    MvcTestResult result = mvc.get().uri("/oauth2/authorization/github").exchange();

    assertThat(result).hasStatus(HttpStatus.FOUND);
    assertThat(result.getResponse().getRedirectedUrl())
        .startsWith("https://github.com/login/oauth/authorize")
        .contains("client_id=test-client-id")
        .contains("scope=read:user%20user:email%20repo%20workflow")
        .contains("state=");
  }

  private MvcTestResult refresh(String refreshToken) {
    return mvc.post()
        .uri("/api/v1/auth/refresh")
        .cookie(new Cookie(COOKIE, refreshToken))
        .exchange();
  }

  private static String rotatedCookie(MvcTestResult result) {
    assertThat(result).hasStatusOk();
    Cookie cookie = result.getResponse().getCookie(COOKIE);
    assertThat(cookie).isNotNull();
    return cookie.getValue();
  }
}
