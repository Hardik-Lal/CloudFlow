package com.cloudflow.deployment.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Image vulnerability scanning with Trivy.
 *
 * @param enabled scan every newly built image
 * @param image Trivy container image
 * @param blockOnCritical fail the deployment when CRITICAL vulnerabilities are found
 * @param cacheVolume Docker volume caching Trivy's vulnerability database between scans
 * @param dockerSocket Docker socket path on the Docker host, mounted into the scanner so it can
 *     read the freshly built local image
 */
@Validated
@ConfigurationProperties("cloudflow.security.scan")
public record ScanProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("aquasec/trivy:0.74.0") @NotBlank String image,
    @DefaultValue("false") boolean blockOnCritical,
    @DefaultValue("10m") @NotNull Duration timeout,
    @DefaultValue("cloudflow-trivy-cache") @NotBlank String cacheVolume,
    @DefaultValue("/var/run/docker.sock") @NotBlank String dockerSocket) {}
