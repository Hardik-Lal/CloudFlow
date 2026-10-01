package com.cloudflow.cicd.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * @param publicApiUrl CloudFlow API URL reachable from GitHub Actions runners (the value of the
 *     {@code CLOUDFLOW_URL} repository secret)
 * @param webhookSecret shared secret for GitHub webhook signatures; webhooks are rejected when
 *     empty
 * @param runsPerSync number of recent workflow runs fetched when syncing from the Actions API
 */
@Validated
@ConfigurationProperties("cloudflow.cicd")
public record CicdProperties(
    @DefaultValue("http://localhost:8080") @NotBlank String publicApiUrl,
    @DefaultValue("") String webhookSecret,
    @DefaultValue("20") @Min(1) @Max(100) int runsPerSync) {

  public boolean webhooksEnabled() {
    return webhookSecret != null && !webhookSecret.isBlank();
  }
}
