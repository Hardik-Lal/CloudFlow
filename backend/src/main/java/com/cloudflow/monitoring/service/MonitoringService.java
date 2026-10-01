package com.cloudflow.monitoring.service;

import com.cloudflow.deployment.dto.ActiveDeployment;
import com.cloudflow.deployment.service.DeploymentQueryService;
import com.cloudflow.environment.service.EnvironmentAccessService;
import com.cloudflow.monitoring.config.MonitoringProperties;
import com.cloudflow.monitoring.domain.HealthCheckResult;
import com.cloudflow.monitoring.dto.EnvironmentEventResponse;
import com.cloudflow.monitoring.dto.EnvironmentMetricsResponse;
import com.cloudflow.monitoring.dto.HealthSummary;
import com.cloudflow.monitoring.dto.MetricSample;
import com.cloudflow.monitoring.dto.ServiceStatus;
import com.cloudflow.monitoring.repository.EnvironmentEventRepository;
import com.cloudflow.monitoring.repository.HealthCheckResultRepository;
import com.cloudflow.organization.domain.Permission;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonitoringService {

  static final int MAX_EVENTS = 200;

  private final EnvironmentAccessService environmentAccess;
  private final DeploymentQueryService deployments;
  private final AppMetricsRegistry metrics;
  private final HealthCheckResultRepository healthChecks;
  private final EnvironmentEventRepository events;
  private final MonitoringProperties properties;
  private final Clock clock;

  public MonitoringService(
      EnvironmentAccessService environmentAccess,
      DeploymentQueryService deployments,
      AppMetricsRegistry metrics,
      HealthCheckResultRepository healthChecks,
      EnvironmentEventRepository events,
      MonitoringProperties properties,
      Clock clock) {
    this.environmentAccess = environmentAccess;
    this.deployments = deployments;
    this.metrics = metrics;
    this.healthChecks = healthChecks;
    this.events = events;
    this.properties = properties;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public EnvironmentMetricsResponse metrics(UUID environmentId, UUID userId) {
    requireRead(environmentId, userId);
    Optional<ActiveDeployment> active = deployments.activeFor(environmentId);
    Optional<MetricSample> latest =
        metrics
            .latest(environmentId)
            .filter(
                sample ->
                    active.isPresent()
                        && sample.deploymentId().equals(active.get().deploymentId()));
    return new EnvironmentMetricsResponse(
        environmentId,
        active.map(ActiveDeployment::deploymentId).orElse(null),
        status(active.isPresent(), latest),
        latest.orElse(null),
        summarize(
            healthChecks.findAllByEnvironmentIdAndCheckedAtAfterOrderByCheckedAtAsc(
                environmentId, clock.instant().minus(properties.summaryWindow()))),
        active.isPresent() ? metrics.history(environmentId) : List.of());
  }

  @Transactional(readOnly = true)
  public List<EnvironmentEventResponse> events(UUID environmentId, UUID userId, int limit) {
    requireRead(environmentId, userId);
    return events
        .findAllByEnvironmentIdOrderByIdDesc(
            environmentId, Limit.of(Math.clamp(limit, 1, MAX_EVENTS)))
        .stream()
        .map(EnvironmentEventResponse::from)
        .toList();
  }

  static ServiceStatus status(boolean deployed, Optional<MetricSample> latest) {
    if (!deployed) {
      return ServiceStatus.NOT_DEPLOYED;
    }
    if (latest.isEmpty()) {
      return ServiceStatus.UNKNOWN;
    }
    MetricSample sample = latest.get();
    if (!sample.running()) {
      return ServiceStatus.DOWN;
    }
    return Boolean.FALSE.equals(sample.healthy()) ? ServiceStatus.DEGRADED : ServiceStatus.UP;
  }

  HealthSummary summarize(List<HealthCheckResult> results) {
    if (results.isEmpty()) {
      return new HealthSummary(properties.summaryWindow(), 0, null, null, null, null);
    }
    long healthy = results.stream().filter(HealthCheckResult::isHealthy).count();
    List<Integer> responseTimes =
        results.stream()
            .filter(result -> result.getStatusCode() != null)
            .map(HealthCheckResult::getResponseTimeMs)
            .sorted()
            .toList();
    double uptime = 100.0 * healthy / results.size();
    return new HealthSummary(
        properties.summaryWindow(),
        results.size(),
        uptime,
        100.0 - uptime,
        responseTimes.isEmpty()
            ? null
            : responseTimes.stream().mapToInt(Integer::intValue).average().orElse(0),
        responseTimes.isEmpty()
            ? null
            : responseTimes.get((int) Math.ceil(0.95 * responseTimes.size()) - 1));
  }

  private void requireRead(UUID environmentId, UUID userId) {
    environmentAccess.require(
        environmentId, userId, Permission.DEPLOYMENT_READ, Permission.DEPLOYMENT_READ);
  }
}
