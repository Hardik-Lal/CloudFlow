package com.cloudflow.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.cloudflow.support.IntegrationTest;
import com.cloudflow.support.TestApi;
import com.cloudflow.support.TestUsers;
import com.cloudflow.support.TestUsers.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@IntegrationTest
@TestPropertySource(
    properties = {"cloudflow.rate-limit.api-per-minute=5", "cloudflow.rate-limit.ai-per-minute=2"})
class RateLimitIntegrationTest {

  @Autowired private TestApi api;
  @Autowired private TestUsers users;

  @Test
  void limitsEachUserSeparatelyWithRetryAfter() {
    TestUser busy = users.create("busy");
    TestUser other = users.create("other");
    for (int i = 0; i < 5; i++) {
      assertThat(api.get(busy, "/api/v1/users/me")).hasStatusOk();
    }

    MvcTestResult limited = api.get(busy, "/api/v1/users/me");

    assertThat(limited)
        .hasStatus(HttpStatus.TOO_MANY_REQUESTS)
        .bodyJson()
        .extractingPath("$.type")
        .isEqualTo("urn:cloudflow:problem:rate-limited");
    assertThat(limited.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isNotBlank();
    assertThat(api.get(other, "/api/v1/users/me")).hasStatusOk();
  }

  @Test
  void aiEndpointsHaveATighterLimit() {
    TestUser user = users.create("ai");
    String path = "/api/v1/projects/00000000-0000-0000-0000-000000000000/assistant/query";
    String body = "{\"question\":\"What is deployed?\"}";

    assertThat(api.post(user, path, body)).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(api.post(user, path, body)).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(api.post(user, path, body)).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
  }

  @Test
  void apiResponsesCarrySecurityHeaders() {
    MvcTestResult response = api.get(users.create("headers"), "/api/v1/users/me");

    assertThat(response.getResponse().getHeader("Content-Security-Policy"))
        .isEqualTo("default-src 'none'; frame-ancestors 'none'");
    assertThat(response.getResponse().getHeader("X-Frame-Options")).isEqualTo("DENY");
    assertThat(response.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    assertThat(response.getResponse().getHeader("Referrer-Policy")).isEqualTo("no-referrer");
  }
}
