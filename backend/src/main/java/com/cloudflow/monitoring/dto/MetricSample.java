package com.cloudflow.monitoring.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One sample of a running deployment.
 *
 * @param healthy result of the HTTP probe; {@code null} if the container could not be probed
 * @param responseTimeMs probe response time, if a response was received
 */
public record MetricSample(
    Instant timestamp,
    UUID deploymentId,
    boolean running,
    Boolean healthy,
    double cpuPercent,
    long memoryBytes,
    long memoryLimitBytes,
    long networkRxBytes,
    long networkTxBytes,
    long uptimeSeconds,
    int restartCount,
    Integer responseTimeMs,
    Integer statusCode) {}
