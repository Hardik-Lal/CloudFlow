package com.cloudflow.monitoring.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * @param interval how often running containers are sampled and probed
 * @param historySamples samples kept in memory per environment for dashboards (Prometheus keeps the
 *     long-term history)
 * @param summaryWindow window for uptime, response time, and error-rate summaries
 * @param healthCheckRetention how long probe results are kept in the database
 * @param eventRetention how long environment events are kept
 */
@Validated
@ConfigurationProperties("cloudflow.monitoring")
public record MonitoringProperties(
    @DefaultValue("15s") @NotNull Duration interval,
    @DefaultValue("240") @Min(10) int historySamples,
    @DefaultValue("1h") @NotNull Duration summaryWindow,
    @DefaultValue("7d") @NotNull Duration healthCheckRetention,
    @DefaultValue("30d") @NotNull Duration eventRetention) {}
