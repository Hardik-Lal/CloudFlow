package com.cloudflow.deployment.engine;

/**
 * Result of one HTTP health probe.
 *
 * @param statusCode HTTP status, or {@code null} if no response was received
 * @param error connection error, or {@code null}
 */
public record HealthProbe(boolean healthy, Integer statusCode, long responseTimeMs, String error) {}
