package com.cloudflow.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudflow.audit.service.AuditLogger;
import com.cloudflow.auth.config.AuthProperties;
import com.cloudflow.auth.domain.RefreshToken;
import com.cloudflow.auth.dto.RefreshTokenRotation;
import com.cloudflow.auth.repository.RefreshTokenRepository;
import com.cloudflow.common.crypto.Hashing;
import com.cloudflow.common.exception.UnauthorizedException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefreshTokenServiceTest {

  private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");
  private static final UUID USER_ID = UUID.randomUUID();

  private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
  private final RefreshTokenService service =
      new RefreshTokenService(
          repository,
          new AuthProperties(
              "x".repeat(32),
              "cloudflow",
              Duration.ofMinutes(15),
              Duration.ofDays(7),
              "cloudflow_refresh",
              false),
          Clock.fixed(NOW, ZoneOffset.UTC),
          mock(AuditLogger.class));

  @Test
  void rotateRevokesPresentedTokenAndIssuesANewOne() {
    RefreshToken token = storedToken("raw", NOW.plus(Duration.ofDays(1)));

    RefreshTokenRotation rotation = service.rotate("raw");

    assertThat(token.isRevoked()).isTrue();
    assertThat(rotation.userId()).isEqualTo(USER_ID);
    assertThat(rotation.newRefreshToken()).isNotBlank().isNotEqualTo("raw");
    verify(repository).save(any(RefreshToken.class));
  }

  @Test
  void rotateRejectsExpiredTokens() {
    storedToken("raw", NOW.minusSeconds(1));

    assertThatThrownBy(() -> service.rotate("raw")).isInstanceOf(UnauthorizedException.class);
  }

  @Test
  void reuseWithinGracePeriodIsRejectedWithoutRevokingOtherSessions() {
    RefreshToken token = storedToken("raw", NOW.plus(Duration.ofDays(1)));
    token.revoke(NOW.minusSeconds(2));

    assertThatThrownBy(() -> service.rotate("raw")).isInstanceOf(UnauthorizedException.class);
    verify(repository, never()).revokeAllForUser(any(), any());
  }

  @Test
  void reuseAfterGracePeriodRevokesAllSessionsOfTheUser() {
    RefreshToken token = storedToken("raw", NOW.plus(Duration.ofDays(1)));
    token.revoke(NOW.minus(RefreshTokenService.REUSE_GRACE_PERIOD).minusSeconds(1));

    assertThatThrownBy(() -> service.rotate("raw")).isInstanceOf(UnauthorizedException.class);
    verify(repository).revokeAllForUser(eq(USER_ID), eq(NOW));
  }

  @Test
  void issuedTokensAreStoredOnlyAsHashes() {
    when(repository.save(any(RefreshToken.class))).thenAnswer(call -> call.getArgument(0));

    String raw = service.issue(USER_ID);

    verify(repository)
        .save(argThat(token -> token.getExpiresAt().equals(NOW.plus(Duration.ofDays(7)))));
    assertThat(raw).hasSizeGreaterThanOrEqualTo(43);
    verify(repository, never()).findByTokenHash(anyString());
  }

  private RefreshToken storedToken(String raw, Instant expiresAt) {
    RefreshToken token = new RefreshToken(USER_ID, Hashing.sha256Hex(raw), expiresAt);
    when(repository.findByTokenHashForUpdate(Hashing.sha256Hex(raw)))
        .thenReturn(Optional.of(token));
    return token;
  }
}
