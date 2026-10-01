package com.cloudflow.common.ratelimit;

import com.cloudflow.common.crypto.Hashing;
import com.cloudflow.common.exception.ProblemTypes;
import com.cloudflow.common.security.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Applies per-client request limits to the API, with tighter limits for session refresh, AI
 * endpoints (they cost money and time), CI deploy hooks, and webhooks. Runs after authentication so
 * authenticated calls are limited per user rather than per IP.
 */
public class RateLimitFilter extends OncePerRequestFilter {

  private static final Pattern AI_PATH =
      Pattern.compile(
          "^/api/v1/(projects/[^/]+/(assistant/query|knowledge/reindex|suggestions)"
              + "|deployments/[^/]+/analysis)$");

  private final TokenBucketRateLimiter limiter;
  private final RateLimitProperties properties;

  public RateLimitFilter(TokenBucketRateLimiter limiter, RateLimitProperties properties) {
    this.limiter = limiter;
    this.properties = properties;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !properties.enabled() || !request.getRequestURI().startsWith("/api/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Limit limit = limitFor(request);
    long retryAfter = limiter.tryAcquire(limit.key(), limit.perMinute());
    if (retryAfter > 0) {
      reject(request, response, retryAfter);
      return;
    }
    chain.doFilter(request, response);
  }

  Limit limitFor(HttpServletRequest request) {
    String path = request.getRequestURI();
    if (path.startsWith("/api/v1/auth/")) {
      return new Limit("auth:" + request.getRemoteAddr(), properties.authPerMinute());
    }
    if (path.startsWith("/api/v1/pipeline-hooks/")) {
      String token = request.getHeader("X-CloudFlow-Deploy-Token");
      String client =
          token == null ? "ip:" + request.getRemoteAddr() : "token:" + Hashing.sha256Hex(token);
      return new Limit("hook:" + client, properties.pipelineHookPerMinute());
    }
    if (path.startsWith("/api/v1/webhooks/")) {
      return new Limit("webhook:" + request.getRemoteAddr(), properties.webhookPerMinute());
    }
    String client = currentClient(request);
    if ("POST".equals(request.getMethod()) && AI_PATH.matcher(path).matches()) {
      return new Limit("ai:" + client, properties.aiPerMinute());
    }
    return new Limit("api:" + client, properties.apiPerMinute());
  }

  private static String currentClient(HttpServletRequest request) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user
        ? "user:" + user.id()
        : "ip:" + request.getRemoteAddr();
  }

  private static void reject(
      HttpServletRequest request, HttpServletResponse response, long retryAfter)
      throws IOException {
    response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
    response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response
        .getWriter()
        .write(
            "{\"type\":\""
                + ProblemTypes.RATE_LIMITED
                + "\",\"title\":\"Too many requests\","
                + "\"status\":429,\"detail\":\"Rate limit exceeded; retry in "
                + retryAfter
                + " seconds\",\"instance\":\""
                + request.getRequestURI().replace("\"", "")
                + "\"}");
  }

  record Limit(String key, int perMinute) {}
}
