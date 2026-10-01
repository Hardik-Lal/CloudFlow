package com.cloudflow.common.ratelimit;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Requests allowed per minute and client. Clients are identified by user (authenticated calls),
 * deploy token (CI), or IP address (anonymous calls).
 */
@Validated
@ConfigurationProperties("cloudflow.rate-limit")
public record RateLimitProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("600") @Min(1) int apiPerMinute,
    @DefaultValue("20") @Min(1) int aiPerMinute,
    @DefaultValue("30") @Min(1) int authPerMinute,
    @DefaultValue("60") @Min(1) int pipelineHookPerMinute,
    @DefaultValue("300") @Min(1) int webhookPerMinute) {}
