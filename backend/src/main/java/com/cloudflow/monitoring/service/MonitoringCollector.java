package com.cloudflow.monitoring.service;

import com.cloudflow.deployment.dto.ActiveDeployment;
import com.cloudflow.deployment.engine.ContainerRuntime;
import com.cloudflow.deployment.engine.ContainerState;
import com.cloudflow.deployment.engine.ContainerStats;
import com.cloudflow.deployment.engine.DeploymentEndpoints;
import com.cloudflow.deployment.engine.HealthChecker;
import com.cloudflow.deployment.engine.HealthProbe;
import com.cloudflow.deployment.service.DeploymentQueryService;
import com.cloudflow.environment.domain.DeploymentSettings;
import com.cloudflow.environment.dto.EnvironmentSnapshot;
import com.cloudflow.environment.service.DeploymentConfigService;
import com.cloudflow.environment.service.EnvironmentLookupService;
import com.cloudflow.monitoring.domain.EventSeverity;
import com.cloudflow.monitoring.domain.EventType;
import com.cloudflow.monitoring.domain.HealthCheckResult;
import com.cloudflow.monitoring.dto.MetricSample;
import com.cloudflow.monitoring.repository.HealthCheckResultRepository;
import com.cloudflow.project.service.ProjectLookupService;
import io.micrometer.core.instrument.Tags;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Samples every live deployment on a fixed interval: container state and resource usage, plus an
 * HTTP probe for health and response time. Health and restart transitions become environment
 * events; samples feed Prometheus gauges and live dashboards.
 */
@Component
public class MonitoringCollector {

  private static final Logger log = LoggerFactory.getLogger(MonitoringCollector.class);
  private static final ContainerStats NO_STATS = new ContainerStats(0, 0, 0, 0, 0);

  private final DeploymentQueryService deployments;
  private final ContainerRuntime runtime;
  private final HealthChecker healthChecker;
  private final DeploymentEndpoints endpoints;
  private final DeploymentConfigService configs;
  private final EnvironmentLookupService environments;
  private final ProjectLookupService projects;
  private final HealthCheckResultRepository healthChecks;
  private final EventRecorder events;
  private final AppMetricsRegistry metrics;
  private final SimpMessagingTemplate messaging;
  private final Clock clock;

  public MonitoringCollector(
      DeploymentQueryService deployments,
      ContainerRuntime runtime,
      HealthChecker healthChecker,
      DeploymentEndpoints endpoints,
      DeploymentConfigService configs,
      EnvironmentLookupService environments,
      ProjectLookupService projects,
      HealthCheckResultRepository healthChecks,
      EventRecorder events,
      AppMetricsRegistry metrics,
      SimpMessagingTemplate messaging,
      Clock clock) {
    this.deployments = deployments;
    this.runtime = runtime;
    this.healthChecker = healthChecker;
    this.endpoints = endpoints;
    this.configs = configs;
    this.environments = environments;
    this.projects = projects;
    this.healthChecks = healthChecks;
    this.events = events;
    this.metrics = metrics;
    this.messaging = messaging;
    this.clock = clock;
  }

  @Scheduled(
      fixedDelayString = "${cloudflow.monitoring.interval:15s}",
      initialDelayString = "${cloudflow.monitoring.initial-delay:10s}")
  public void collectAll() {
    List<ActiveDeployment> active = deployments.allActive();
    metrics.retainOnly(
        active.stream().map(ActiveDeployment::environmentId).collect(Collectors.toSet()));
    for (ActiveDeployment deployment : active) {
      try {
        collect(deployment);
      } catch (RuntimeException e) {
        log.warn("Monitoring failed for environment {}", deployment.environmentId(), e);
      }
    }
  }

  /** Samples one live deployment. Package-private for tests. */
  void collect(ActiveDeployment deployment) {
    UUID environmentId = deployment.environmentId();
    Instant now = clock.instant();
    Optional<MetricSample> previous =
        metrics
            .latest(environmentId)
            .filter(s -> s.deploymentId().equals(deployment.deploymentId()));
    ContainerState state =
        deployment.containerId() == null
            ? ContainerState.missing()
            : runtime.inspect(deployment.containerId());

    MetricSample sample;
    if (!state.exists() || !state.running()) {
      if (previous.isEmpty() || previous.get().running()) {
        events.record(
            environmentId,
            deployment.deploymentId(),
            EventType.CONTAINER_STOPPED,
            EventSeverity.ERROR,
            "The container is not running ("
                + state.status()
                + (state.exitCode() == null ? "" : ", exit code " + state.exitCode())
                + ")");
      }
      healthChecks.save(
          new HealthCheckResult(
              environmentId,
              deployment.deploymentId(),
              false,
              null,
              0,
              "Container not running",
              now));
      sample =
          new MetricSample(
              now,
              deployment.deploymentId(),
              false,
              false,
              0,
              0,
              0,
              0,
              0,
              0,
              state.restartCount(),
              null,
              null);
    } else {
      ContainerStats stats = runtime.stats(deployment.containerId()).orElse(NO_STATS);
      DeploymentSettings settings = configs.settings(environmentId);
      URI url =
          endpoints.probeUrl(
              deployment.target(),
              deployment.containerName(),
              deployment.hostPort(),
              settings.containerPort(),
              settings.healthCheckPath());
      HealthProbe probe = url == null ? null : healthChecker.probe(url);
      if (probe != null) {
        healthChecks.save(
            new HealthCheckResult(
                environmentId,
                deployment.deploymentId(),
                probe.healthy(),
                probe.statusCode(),
                (int) probe.responseTimeMs(),
                probe.error(),
                now));
      }
      sample =
          new MetricSample(
              now,
              deployment.deploymentId(),
              true,
              probe == null ? null : probe.healthy(),
              stats.cpuPercent(),
              stats.memoryUsageBytes(),
              stats.memoryLimitBytes(),
              stats.networkRxBytes(),
              stats.networkTxBytes(),
              state.startedAt() == null ? 0 : Duration.between(state.startedAt(), now).toSeconds(),
              state.restartCount(),
              probe == null || probe.statusCode() == null ? null : (int) probe.responseTimeMs(),
              probe == null ? null : probe.statusCode());
      previous.ifPresent(before -> recordTransitions(deployment, before, sample, probe));
    }

    metrics.record(environmentId, tags(environmentId), sample);
    messaging.convertAndSend("/topic/environments/" + environmentId + "/metrics", sample);
  }

  private void recordTransitions(
      ActiveDeployment deployment, MetricSample before, MetricSample after, HealthProbe probe) {
    UUID environmentId = deployment.environmentId();
    if (Boolean.TRUE.equals(before.healthy()) && Boolean.FALSE.equals(after.healthy())) {
      events.record(
          environmentId,
          deployment.deploymentId(),
          EventType.HEALTH_DEGRADED,
          EventSeverity.WARN,
          "Health check failing: "
              + (probe.statusCode() != null ? "HTTP " + probe.statusCode() : probe.error()));
    } else if (Boolean.FALSE.equals(before.healthy()) && Boolean.TRUE.equals(after.healthy())) {
      events.record(
          environmentId,
          deployment.deploymentId(),
          EventType.HEALTH_RECOVERED,
          EventSeverity.INFO,
          "Health check passing again (HTTP " + probe.statusCode() + ")");
    }
    if (after.restartCount() > before.restartCount()) {
      events.record(
          environmentId,
          deployment.deploymentId(),
          EventType.CONTAINER_RESTARTED,
          EventSeverity.WARN,
          "The container restarted (restart #" + after.restartCount() + ")");
    }
  }

  private Tags tags(UUID environmentId) {
    EnvironmentSnapshot environment = environments.snapshot(environmentId);
    return Tags.of(
        "project", projects.snapshot(environment.projectId()).slug(),
        "environment", environment.type().name().toLowerCase(Locale.ROOT),
        "environment_id", environmentId.toString());
  }
}
