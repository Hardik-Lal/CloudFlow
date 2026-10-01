package com.cloudflow.monitoring.dto;

import java.util.List;
import java.util.UUID;

/**
 * @param current the latest sample, if any
 * @param history recent samples, oldest first
 */
public record EnvironmentMetricsResponse(
    UUID environmentId,
    UUID deploymentId,
    ServiceStatus status,
    MetricSample current,
    HealthSummary health,
    List<MetricSample> history) {}
