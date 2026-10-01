package com.cloudflow.github.config;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * @param apiBaseUrl GitHub REST API base URL (override for GitHub Enterprise)
 * @param timeout connect and read timeout for GitHub calls
 */
@Validated
@ConfigurationProperties("cloudflow.github")
public record GithubProperties(
    @DefaultValue("https://api.github.com") @NotBlank String apiBaseUrl,
    @DefaultValue("10s") Duration timeout) {}
