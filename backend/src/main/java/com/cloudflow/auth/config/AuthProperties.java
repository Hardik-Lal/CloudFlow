package com.cloudflow.auth.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Token and cookie settings for authentication.
 *
 * @param jwtSecret HMAC-SHA256 signing secret for access tokens (at least 32 characters)
 * @param jwtIssuer {@code iss} claim written to and required in access tokens
 * @param accessTokenTtl lifetime of access tokens
 * @param refreshTokenTtl lifetime of refresh tokens
 * @param refreshCookieName name of the HttpOnly cookie carrying the refresh token
 * @param refreshCookieSecure whether the refresh cookie requires HTTPS (true outside localhost)
 */
@Validated
@ConfigurationProperties("cloudflow.auth")
public record AuthProperties(
    @NotBlank @Size(min = 32) String jwtSecret,
    @NotBlank String jwtIssuer,
    @NotNull Duration accessTokenTtl,
    @NotNull Duration refreshTokenTtl,
    @NotBlank String refreshCookieName,
    boolean refreshCookieSecure) {}
