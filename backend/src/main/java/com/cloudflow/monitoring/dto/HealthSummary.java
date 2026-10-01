package com.cloudflow.monitoring.dto;

import java.time.Duration;

/**
 * Probe statistics over {@code window}.
 *
 * @param uptimePercent share of healthy probes, or {@code null} without probes
 * @param errorRatePercent share of failed probes (non-2xx/3xx or no response)
 */
public record HealthSummary(
    Duration window,
    int checks,
    Double uptimePercent,
    Double errorRatePercent,
    Double averageResponseTimeMs,
    Integer p95ResponseTimeMs) {}
