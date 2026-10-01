package com.cloudflow.monitoring.service;

import com.cloudflow.monitoring.config.MonitoringProperties;
import com.cloudflow.monitoring.dto.MetricSample;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToDoubleFunction;
import org.springframework.stereotype.Component;

/**
 * Latest samples of every live deployment, exposed as Prometheus gauges (tagged by project and
 * environment) plus a short in-memory history for dashboards.
 */
@Component
public class AppMetricsRegistry {

  private final MeterRegistry meterRegistry;
  private final MonitoringProperties properties;
  private final Map<UUID, EnvironmentMetrics> environments = new ConcurrentHashMap<>();

  public AppMetricsRegistry(MeterRegistry meterRegistry, MonitoringProperties properties) {
    this.meterRegistry = meterRegistry;
    this.properties = properties;
  }

  public void record(UUID environmentId, Tags tags, MetricSample sample) {
    EnvironmentMetrics metrics =
        environments.computeIfAbsent(environmentId, id -> new EnvironmentMetrics(tags));
    metrics.add(sample, properties.historySamples());
    if (sample.healthy() != null) {
      (sample.healthy() ? metrics.checksPassed : metrics.checksFailed).increment();
    }
  }

  public Optional<MetricSample> latest(UUID environmentId) {
    return Optional.ofNullable(environments.get(environmentId)).map(EnvironmentMetrics::latest);
  }

  public List<MetricSample> history(UUID environmentId) {
    EnvironmentMetrics metrics = environments.get(environmentId);
    return metrics == null ? List.of() : metrics.history();
  }

  /** Drops metrics of environments that no longer have a live deployment. */
  public void retainOnly(Set<UUID> environmentIds) {
    for (UUID environmentId : List.copyOf(environments.keySet())) {
      if (!environmentIds.contains(environmentId)) {
        EnvironmentMetrics removed = environments.remove(environmentId);
        removed.meters.forEach(meterRegistry::remove);
      }
    }
  }

  /** Per-environment gauges read the latest sample, so they always report current values. */
  private final class EnvironmentMetrics {
    private final Deque<MetricSample> samples = new ArrayDeque<>();
    private final Collection<Meter> meters = new ArrayList<>();
    private final Counter checksPassed;
    private final Counter checksFailed;
    private volatile MetricSample latest;

    EnvironmentMetrics(Tags tags) {
      gauge(
          "cloudflow.app.up",
          tags,
          null,
          s -> s.running() && Boolean.TRUE.equals(s.healthy()) ? 1 : 0);
      gauge("cloudflow.app.cpu.usage", tags, "percent", MetricSample::cpuPercent);
      gauge("cloudflow.app.memory.usage", tags, "bytes", MetricSample::memoryBytes);
      gauge("cloudflow.app.memory.limit", tags, "bytes", MetricSample::memoryLimitBytes);
      gauge("cloudflow.app.network.received", tags, "bytes", MetricSample::networkRxBytes);
      gauge("cloudflow.app.network.transmitted", tags, "bytes", MetricSample::networkTxBytes);
      gauge("cloudflow.app.uptime", tags, "seconds", MetricSample::uptimeSeconds);
      gauge("cloudflow.app.restarts", tags, null, MetricSample::restartCount);
      gauge(
          "cloudflow.app.response.time",
          tags,
          "milliseconds",
          s -> s.responseTimeMs() == null ? Double.NaN : s.responseTimeMs());
      checksPassed = counter(tags, "success");
      checksFailed = counter(tags, "failure");
    }

    synchronized void add(MetricSample sample, int limit) {
      latest = sample;
      samples.addLast(sample);
      while (samples.size() > limit) {
        samples.removeFirst();
      }
    }

    MetricSample latest() {
      return latest;
    }

    synchronized List<MetricSample> history() {
      return List.copyOf(samples);
    }

    private void gauge(
        String name, Tags tags, String baseUnit, ToDoubleFunction<MetricSample> value) {
      meters.add(
          Gauge.builder(
                  name,
                  this,
                  metrics ->
                      metrics.latest == null ? Double.NaN : value.applyAsDouble(metrics.latest))
              .tags(tags)
              .baseUnit(baseUnit)
              .register(meterRegistry));
    }

    private Counter counter(Tags tags, String result) {
      Counter counter =
          Counter.builder("cloudflow.app.health.checks")
              .tags(tags.and("result", result))
              .register(meterRegistry);
      meters.add(counter);
      return counter;
    }
  }
}
