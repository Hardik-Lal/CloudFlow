package com.cloudflow.auth.config;

import com.cloudflow.auth.oauth.GithubOAuth2UserService;
import com.cloudflow.auth.oauth.OAuth2LoginFailureHandler;
import com.cloudflow.auth.oauth.OAuth2LoginSuccessHandler;
import com.cloudflow.auth.security.AuthenticatedUserConverter;
import com.cloudflow.auth.security.SecurityProblemHandler;
import com.cloudflow.common.ratelimit.RateLimitConfig;
import com.cloudflow.common.ratelimit.RateLimitProperties;
import com.cloudflow.common.ratelimit.TokenBucketRateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.firewall.HttpStatusRequestRejectedHandler;
import org.springframework.security.web.firewall.RequestRejectedHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Two filter chains:
 *
 * <ol>
 *   <li>The GitHub OAuth login flow, which needs a short-lived HTTP session to hold OAuth state
 *       between the redirect to GitHub and the callback.
 *   <li>The stateless REST API, authenticated with JWT bearer tokens.
 * </ol>
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

  /**
   * Requests rejected by the HTTP firewall (e.g. malformed headers or encoded path traversal) are
   * client errors: answer 400 instead of letting them surface as 500.
   */
  @Bean
  RequestRejectedHandler requestRejectedHandler() {
    return new HttpStatusRequestRejectedHandler();
  }

  @Bean
  @Order(1)
  SecurityFilterChain oauthLoginFilterChain(
      HttpSecurity http,
      GithubOAuth2UserService githubOAuth2UserService,
      OAuth2LoginSuccessHandler successHandler,
      OAuth2LoginFailureHandler failureHandler)
      throws Exception {
    http.securityMatcher("/oauth2/**", "/login/oauth2/**")
        .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
        .oauth2Login(
            oauth2 ->
                oauth2
                    .userInfoEndpoint(userInfo -> userInfo.userService(githubOAuth2UserService))
                    // Keep the GitHub token out of the default in-memory store; it is persisted
                    // encrypted by GithubOAuth2UserService and the session is discarded afterwards.
                    .authorizedClientRepository(new HttpSessionOAuth2AuthorizedClientRepository())
                    .successHandler(successHandler)
                    .failureHandler(failureHandler));
    return http.build();
  }

  @Bean
  @Order(2)
  SecurityFilterChain apiFilterChain(
      HttpSecurity http,
      AuthenticatedUserConverter authenticatedUserConverter,
      SecurityProblemHandler securityProblemHandler,
      TokenBucketRateLimiter rateLimiter,
      RateLimitProperties rateLimitProperties,
      @Value("${management.server.port:}") String managementPort)
      throws Exception {
    http.addFilterAfter(
            RateLimitConfig.filter(rateLimiter, rateLimitProperties),
            BearerTokenAuthenticationFilter.class)
        .headers(
            headers ->
                headers
                    // JSON API: nothing may be rendered, framed, or loaded from its responses.
                    .contentSecurityPolicy(
                        csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                    .referrerPolicy(
                        referrer ->
                            referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                    .frameOptions(frame -> frame.deny()))
        .cors(Customizer.withDefaults())
        // Bearer tokens are not sent automatically by browsers, so CSRF does not apply; the
        // cookie-based auth endpoints rely on SameSite=Strict instead.
        .csrf(AbstractHttpConfigurer::disable)
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    // Prometheus scrapes over the internal management port only.
                    .requestMatchers(onManagementPort(managementPort, "/actuator/prometheus"))
                    .permitAll()
                    // Metrics span all organizations: never serve other actuator endpoints to
                    // API users, even authenticated ones.
                    .requestMatchers("/actuator/**")
                    .denyAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh", "/api/v1/auth/logout")
                    .permitAll()
                    // Authenticated in the controllers: deploy tokens (CI) and HMAC (webhooks).
                    .requestMatchers("/api/v1/pipeline-hooks/**", "/api/v1/webhooks/github")
                    .permitAll()
                    // WebSocket handshake; STOMP CONNECT carries the JWT (see WebSocketConfig).
                    .requestMatchers("/ws")
                    .permitAll()
                    .requestMatchers("/error")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            resourceServer ->
                resourceServer
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(authenticatedUserConverter))
                    .authenticationEntryPoint(securityProblemHandler)
                    .accessDeniedHandler(securityProblemHandler))
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(securityProblemHandler)
                    .accessDeniedHandler(securityProblemHandler));
    return http.build();
  }

  /**
   * Matches {@code path} only when the request arrived on the separate management port. With no
   * separate port configured, nothing matches, so the endpoint stays protected.
   */
  static RequestMatcher onManagementPort(String managementPort, String path) {
    if (managementPort == null || managementPort.isBlank()) {
      return request -> false;
    }
    int port = Integer.parseInt(managementPort.strip());
    return request -> request.getLocalPort() == port && path.equals(request.getRequestURI());
  }
}
