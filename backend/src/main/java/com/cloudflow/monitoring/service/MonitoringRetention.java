package com.cloudflow.monitoring.service;

import com.cloudflow.monitoring.config.MonitoringProperties;
import com.cloudflow.monitoring.repository.EnvironmentEventRepository;
import com.cloudflow.monitoring.repository.HealthCheckResultRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Deletes probe results and events older than their retention periods, once a day. */
@Component
public class MonitoringRetention {

  private static final Logger log = LoggerFactory.getLogger(MonitoringRetention.class);

  private final HealthCheckResultRepository healthChecks;
  private final EnvironmentEventRepository events;
  private final MonitoringProperties properties;
  private final Clock clock;

  public MonitoringRetention(
      HealthCheckResultRepository healthChecks,
      EnvironmentEventRepository events,
      MonitoringProperties properties,
      Clock clock) {
    this.healthChecks = healthChecks;
    this.events = events;
    this.properties = properties;
    this.clock = clock;
  }

  @Scheduled(cron = "${cloudflow.monitoring.retention-cron:0 30 3 * * *}")
  @Transactional
  public void purge() {
    int checks =
        healthChecks.deleteOlderThan(clock.instant().minus(properties.healthCheckRetention()));
    int removedEvents = events.deleteOlderThan(clock.instant().minus(properties.eventRetention()));
    log.info("Monitoring retention removed {} health checks and {} events", checks, removedEvents);
  }
}
