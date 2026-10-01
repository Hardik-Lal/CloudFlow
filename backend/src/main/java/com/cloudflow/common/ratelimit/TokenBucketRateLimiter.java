package com.cloudflow.common.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token buckets keyed by client. A bucket holds up to {@code capacity} tokens and refills
 * continuously at {@code capacity} per minute, so short bursts are allowed while the sustained rate
 * is capped.
 *
 * <p>State is in memory, which suits a single backend instance; several instances would need a
 * shared store (e.g. Redis).
 */
public class TokenBucketRateLimiter {

  private static final Duration IDLE_EXPIRY = Duration.ofMinutes(10);

  private final Clock clock;
  private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

  public TokenBucketRateLimiter(Clock clock) {
    this.clock = clock;
  }

  /**
   * @return 0 if the request is allowed, otherwise the seconds until a token is available
   */
  public long tryAcquire(String key, int perMinute) {
    long now = clock.millis();
    Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(perMinute, now));
    return bucket.tryTake(perMinute, now);
  }

  /** Forgets clients that have been idle, bounding memory. */
  public void evictIdle() {
    long cutoff = clock.millis() - IDLE_EXPIRY.toMillis();
    buckets.values().removeIf(bucket -> bucket.lastSeen() < cutoff);
  }

  int size() {
    return buckets.size();
  }

  private static final class Bucket {
    private double tokens;
    private long updatedAt;

    Bucket(int capacity, long now) {
      this.tokens = capacity;
      this.updatedAt = now;
    }

    synchronized long tryTake(int capacity, long now) {
      double refillPerMilli = capacity / 60_000.0;
      tokens = Math.min(capacity, tokens + (now - updatedAt) * refillPerMilli);
      updatedAt = now;
      if (tokens >= 1) {
        tokens -= 1;
        return 0;
      }
      return (long) Math.ceil((1 - tokens) / refillPerMilli / 1000.0);
    }

    synchronized long lastSeen() {
      return updatedAt;
    }
  }
}
