package com.cloudflow.deployment.engine;

/**
 * Point-in-time resource usage of a container.
 *
 * @param cpuPercent CPU usage as a percentage of one core (can exceed 100 on multiple cores)
 * @param memoryUsageBytes working-set memory (page cache excluded)
 * @param memoryLimitBytes memory limit applied to the container
 * @param networkRxBytes total bytes received since the container started
 * @param networkTxBytes total bytes sent since the container started
 */
public record ContainerStats(
    double cpuPercent,
    long memoryUsageBytes,
    long memoryLimitBytes,
    long networkRxBytes,
    long networkTxBytes) {}
