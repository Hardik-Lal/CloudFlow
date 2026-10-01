package com.cloudflow.deployment.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Deployment engine settings.
 *
 * @param dockerHost Docker Engine endpoint (Unix socket or tcp://)
 * @param registry container registry that built images are tagged for and pushed to
 * @param pushImages whether images are pushed to the registry (needed for rollback on other hosts)
 * @param appNetwork Docker network that deployed applications join, isolated from the platform
 * @param workDirectory scratch space for source checkouts
 * @param maxSourceSizeMb upper bound on an extracted repository
 * @param buildTimeout maximum duration of an image build
 * @param healthCheckTimeout how long a new container may take to become healthy
 * @param healthCheckInterval pause between health probes
 * @param healthCheckHost when set, probes go to {@code host:publishedPort} (backend running outside
 *     Docker); when empty, probes go to the container name over the application network
 * @param publicHost host name used to build the URL of a deployed application
 * @param concurrency number of deployments that can run at the same time
 * @param queueCapacity deployments waiting for a free worker before new ones are rejected
 */
@Validated
@ConfigurationProperties("cloudflow.deployment")
public record DeploymentProperties(
    @DefaultValue("unix:///var/run/docker.sock") @NotBlank String dockerHost,
    @DefaultValue("localhost:5000") @NotBlank String registry,
    @DefaultValue("true") boolean pushImages,
    @DefaultValue("cloudflow-apps") @NotBlank String appNetwork,
    @NotNull Path workDirectory,
    @DefaultValue("500") @Min(1) int maxSourceSizeMb,
    @DefaultValue("20m") @NotNull Duration buildTimeout,
    @DefaultValue("90s") @NotNull Duration healthCheckTimeout,
    @DefaultValue("2s") @NotNull Duration healthCheckInterval,
    @DefaultValue("") String healthCheckHost,
    @DefaultValue("localhost") @NotBlank String publicHost,
    @DefaultValue("2") @Min(1) int concurrency,
    @DefaultValue("50") @Min(0) int queueCapacity) {

  public boolean probesViaPublishedPort() {
    return healthCheckHost != null && !healthCheckHost.isBlank();
  }
}
