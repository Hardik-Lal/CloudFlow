package com.cloudflow.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TokenBucketRateLimiterTest {

  private final MutableClock clock = new MutableClock();
  private final TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(clock);

  @Test
  void allowsABurstUpToTheLimitThenAsksToWait() {
    for (int i = 0; i < 3; i++) {
      assertThat(limiter.tryAcquire("client", 3)).isZero();
    }
    assertThat(limiter.tryAcquire("client", 3)).isEqualTo(20);
  }

  @Test
  void refillsContinuously() {
    for (int i = 0; i < 60; i++) {
      limiter.tryAcquire("client", 60);
    }
    assertThat(limiter.tryAcquire("client", 60)).isPositive();

    clock.advance(Duration.ofSeconds(1));

    assertThat(limiter.tryAcquire("client", 60)).isZero();
  }

  @Test
  void clientsAreLimitedIndependentlyAndIdleOnesAreEvicted() {
    limiter.tryAcquire("a", 1);
    assertThat(limiter.tryAcquire("a", 1)).isPositive();
    assertThat(limiter.tryAcquire("b", 1)).isZero();

    clock.advance(Duration.ofMinutes(11));
    limiter.evictIdle();

    assertThat(limiter.size()).isZero();
  }

  private static final class MutableClock extends Clock {
    private Instant now = Instant.parse("2026-09-26T10:00:00Z");

    void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
