package com.cloudflow.deployment.engine;

import com.cloudflow.deployment.domain.DeploymentLog;
import com.cloudflow.deployment.domain.LogLevel;
import com.cloudflow.deployment.domain.LogPhase;
import com.cloudflow.deployment.dto.DeploymentLogResponse;
import com.cloudflow.deployment.repository.DeploymentLogRepository;
import java.time.Clock;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.context.ApplicationEventPublisher;

/** Writes one deployment's log lines, masking secrets and truncating oversized lines. */
public class DeploymentLogSink {

  static final int MAX_LINE_LENGTH = 4000;

  private final UUID deploymentId;
  private final DeploymentLogRepository repository;
  private final SecretMasker masker;
  private final Clock clock;
  private final ApplicationEventPublisher events;

  DeploymentLogSink(
      UUID deploymentId,
      DeploymentLogRepository repository,
      SecretMasker masker,
      Clock clock,
      ApplicationEventPublisher events) {
    this.deploymentId = deploymentId;
    this.repository = repository;
    this.masker = masker;
    this.clock = clock;
    this.events = events;
  }

  public void info(LogPhase phase, String message) {
    write(phase, LogLevel.INFO, message);
  }

  public void warn(LogPhase phase, String message) {
    write(phase, LogLevel.WARN, message);
  }

  public void error(LogPhase phase, String message) {
    write(phase, LogLevel.ERROR, message);
  }

  /** A line consumer for streamed output such as build logs. */
  public Consumer<String> lines(LogPhase phase) {
    return line -> info(phase, line);
  }

  private void write(LogPhase phase, LogLevel level, String message) {
    String text = masker.mask(message == null ? "" : message.stripTrailing());
    if (text.length() > MAX_LINE_LENGTH) {
      text = text.substring(0, MAX_LINE_LENGTH) + " …[truncated]";
    }
    DeploymentLog saved =
        repository.save(new DeploymentLog(deploymentId, phase, level, text, clock.instant()));
    // Streamed to subscribed browsers (the line is already masked).
    events.publishEvent(
        new DeploymentLogAppendedEvent(deploymentId, DeploymentLogResponse.from(saved)));
  }
}
