package com.cloudflow.auth.service;

import com.cloudflow.audit.domain.AuditAction;
import com.cloudflow.audit.service.AuditLogger;
import com.cloudflow.auth.config.AuthProperties;
import com.cloudflow.auth.domain.RefreshToken;
import com.cloudflow.auth.dto.RefreshTokenRotation;
import com.cloudflow.auth.repository.RefreshTokenRepository;
import com.cloudflow.common.crypto.Hashing;
import com.cloudflow.common.exception.UnauthorizedException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opaque refresh tokens with rotation and reuse detection.
 *
 * <p>Every successful refresh revokes the presented token and issues a new one. If a token that was
 * already revoked is presented again, it has most likely been stolen, so all of the user's refresh
 * tokens are revoked and the user must sign in again.
 */
@Service
public class RefreshTokenService {

  private final AuditLogger audit;

  private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
  private static final int TOKEN_BYTES = 32;
  static final Duration REUSE_GRACE_PERIOD = Duration.ofSeconds(10);

  private final RefreshTokenRepository repository;
  private final AuthProperties properties;
  private final Clock clock;
  private final SecureRandom secureRandom = new SecureRandom();

  public RefreshTokenService(
      RefreshTokenRepository repository,
      AuthProperties properties,
      Clock clock,
      AuditLogger audit) {
    this.audit = audit;
    this.repository = repository;
    this.properties = properties;
    this.clock = clock;
  }

  /** Creates a new refresh token for the user and returns its raw value (never stored). */
  @Transactional
  public String issue(UUID userId) {
    byte[] bytes = new byte[TOKEN_BYTES];
    secureRandom.nextBytes(bytes);
    String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    Instant expiresAt = clock.instant().plus(properties.refreshTokenTtl());
    repository.save(new RefreshToken(userId, Hashing.sha256Hex(rawToken), expiresAt));
    return rawToken;
  }

  /**
   * Exchanges a valid refresh token for a new one.
   *
   * @throws UnauthorizedException if the token is unknown, expired, or was already used
   */
  // The reuse-detection revocation must be committed even though the call fails.
  @Transactional(noRollbackFor = UnauthorizedException.class)
  public RefreshTokenRotation rotate(String rawToken) {
    Instant now = clock.instant();
    RefreshToken token =
        repository
            .findByTokenHashForUpdate(Hashing.sha256Hex(rawToken))
            .orElseThrow(() -> new UnauthorizedException("Unknown refresh token"));

    if (token.isRevoked()) {
      if (token.getRevokedAt().plus(REUSE_GRACE_PERIOD).isAfter(now)) {
        throw new UnauthorizedException("Refresh token was just rotated");
      }
      int revoked = repository.revokeAllForUser(token.getUserId(), now);
      log.warn(
          "Refresh token reuse detected for user {}; revoked {} active tokens",
          token.getUserId(),
          revoked);
      audit.recordWithUsername(
          null,
          token.getUserId(),
          null,
          AuditAction.REFRESH_TOKEN_REUSE_DETECTED,
          "user",
          token.getUserId(),
          Map.of("revokedSessions", String.valueOf(revoked)));
      throw new UnauthorizedException("Refresh token has already been used");
    }
    if (token.isExpired(now)) {
      throw new UnauthorizedException("Refresh token has expired");
    }

    token.revoke(now);
    return new RefreshTokenRotation(token.getUserId(), issue(token.getUserId()));
  }

  /** Revokes the token if it exists. Unknown tokens are ignored so logout is idempotent. */
  @Transactional
  public void revoke(String rawToken) {
    repository
        .findByTokenHash(Hashing.sha256Hex(rawToken))
        .filter(token -> !token.isRevoked())
        .ifPresent(
            token -> {
              token.revoke(clock.instant());
              audit.recordWithUsername(
                  null,
                  token.getUserId(),
                  null,
                  AuditAction.USER_SIGNED_OUT,
                  "user",
                  token.getUserId(),
                  Map.of());
            });
  }

  /** Removes expired tokens daily; revoked-but-unexpired tokens are kept for reuse detection. */
  @Scheduled(cron = "${cloudflow.auth.refresh-token-cleanup-cron:0 0 3 * * *}")
  @Transactional
  public void deleteExpiredTokens() {
    int deleted = repository.deleteExpiredBefore(clock.instant());
    if (deleted > 0) {
      log.info("Deleted {} expired refresh tokens", deleted);
    }
  }
}
