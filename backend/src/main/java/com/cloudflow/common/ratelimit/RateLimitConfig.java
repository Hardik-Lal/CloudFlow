package com.cloudflow.common.ratelimit;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
public class RateLimitConfig {

  @Bean
  TokenBucketRateLimiter tokenBucketRateLimiter(Clock clock) {
    return new TokenBucketRateLimiter(clock);
  }

  /** Not a bean on purpose: it is added to the security filter chain, not the servlet chain. */
  public static RateLimitFilter filter(
      TokenBucketRateLimiter limiter, RateLimitProperties properties) {
    return new RateLimitFilter(limiter, properties);
  }

  @Bean
  IdleBucketEviction idleBucketEviction(TokenBucketRateLimiter limiter) {
    return new IdleBucketEviction(limiter);
  }

  /** Periodically drops buckets of clients that stopped calling. */
  public static class IdleBucketEviction {
    private final TokenBucketRateLimiter limiter;

    IdleBucketEviction(TokenBucketRateLimiter limiter) {
      this.limiter = limiter;
    }

    @Scheduled(fixedDelay = 300_000)
    public void evict() {
      limiter.evictIdle();
    }
  }
}
