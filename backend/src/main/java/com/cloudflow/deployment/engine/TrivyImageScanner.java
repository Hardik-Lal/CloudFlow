package com.cloudflow.deployment.engine;

import com.cloudflow.deployment.config.ScanProperties;
import com.cloudflow.deployment.engine.ScanResult.Finding;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.exception.DockerClientException;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.api.model.Volume;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs Trivy as a short-lived container next to the Docker Engine. The scanner reads the freshly
 * built image through the Docker socket and caches its vulnerability database in a named volume.
 */
@Component
public class TrivyImageScanner implements ImageScanner {

  private static final Logger log = LoggerFactory.getLogger(TrivyImageScanner.class);
  private static final int MAX_OUTPUT_BYTES = 20 * 1024 * 1024;

  private final DockerClient docker;
  private final ScanProperties properties;
  private final JsonMapper jsonMapper;

  public TrivyImageScanner(DockerClient docker, ScanProperties properties, JsonMapper jsonMapper) {
    this.docker = docker;
    this.properties = properties;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public Optional<ScanResult> scan(String imageTag, Consumer<String> output) {
    if (!properties.enabled()) {
      return Optional.empty();
    }
    String containerId = null;
    try {
      ensureImage(output);
      containerId =
          docker
              .createContainerCmd(properties.image())
              .withCmd(
                  "image",
                  "--quiet",
                  "--format",
                  "json",
                  "--scanners",
                  "vuln",
                  "--severity",
                  "HIGH,CRITICAL",
                  "--timeout",
                  properties.timeout().toSeconds() + "s",
                  imageTag)
              .withHostConfig(
                  HostConfig.newHostConfig()
                      .withBinds(
                          new Bind(properties.dockerSocket(), new Volume("/var/run/docker.sock")),
                          new Bind(properties.cacheVolume(), new Volume("/root/.cache"))))
              .withLabels(Map.of("cloudflow.managed", "scanner"))
              .exec()
              .getId();
      docker.startContainerCmd(containerId).exec();
      int exitCode =
          docker
              .waitContainerCmd(containerId)
              .exec(new WaitContainerResultCallback())
              .awaitStatusCode(properties.timeout().toSeconds() + 60, TimeUnit.SECONDS);
      Output result = collectOutput(containerId);
      if (exitCode != 0) {
        output.accept(
            "Vulnerability scan failed (exit code " + exitCode + "): " + lastLine(result.stderr()));
        return Optional.empty();
      }
      return Optional.of(parse(result.stdout()));
    } catch (DockerException | DockerClientException e) {
      output.accept("Vulnerability scan could not run: " + e.getMessage());
      return Optional.empty();
    } catch (RuntimeException e) {
      log.warn("Vulnerability scan of {} failed", imageTag, e);
      output.accept("Vulnerability scan could not run: " + e.getMessage());
      return Optional.empty();
    } finally {
      if (containerId != null) {
        try {
          docker.removeContainerCmd(containerId).withForce(true).exec();
        } catch (NotFoundException e) {
          // Already removed.
        }
      }
    }
  }

  /** Parses Trivy's JSON report (only HIGH and CRITICAL were requested). */
  ScanResult parse(String json) {
    JsonNode report = jsonMapper.readTree(json.isBlank() ? "{}" : json);
    List<Finding> findings = new ArrayList<>();
    for (JsonNode target : report.path("Results")) {
      for (JsonNode vulnerability : target.path("Vulnerabilities")) {
        findings.add(
            new Finding(
                vulnerability.path("VulnerabilityID").asString(""),
                vulnerability.path("Severity").asString(""),
                vulnerability.path("PkgName").asString(""),
                vulnerability.path("InstalledVersion").asString(""),
                vulnerability.path("FixedVersion").asString(""),
                vulnerability.path("Title").asString("")));
      }
    }
    int critical = (int) findings.stream().filter(f -> "CRITICAL".equals(f.severity())).count();
    int high = (int) findings.stream().filter(f -> "HIGH".equals(f.severity())).count();
    findings.sort(
        Comparator.comparing((Finding f) -> "CRITICAL".equals(f.severity()) ? 0 : 1)
            .thenComparing(Finding::id));
    return new ScanResult(critical, high, findings);
  }

  private void ensureImage(Consumer<String> output) {
    try {
      docker.inspectImageCmd(properties.image()).exec();
    } catch (NotFoundException e) {
      output.accept("Pulling scanner image " + properties.image());
      try {
        docker.pullImageCmd(properties.image()).start().awaitCompletion(5, TimeUnit.MINUTES);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while pulling the scanner image", interrupted);
      }
    }
  }

  private Output collectOutput(String containerId) {
    StringBuilder stdout = new StringBuilder();
    StringBuilder stderr = new StringBuilder();
    try {
      docker
          .logContainerCmd(containerId)
          .withStdOut(true)
          .withStdErr(true)
          .exec(
              new ResultCallback.Adapter<Frame>() {
                @Override
                public void onNext(Frame frame) {
                  StringBuilder target =
                      frame.getStreamType() == StreamType.STDERR ? stderr : stdout;
                  if (target.length() < MAX_OUTPUT_BYTES) {
                    target.append(new String(frame.getPayload(), StandardCharsets.UTF_8));
                  }
                }
              })
          .awaitCompletion(60, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return new Output(stdout.toString(), stderr.toString());
  }

  private static String lastLine(String text) {
    List<String> lines = text.lines().filter(line -> !line.isBlank()).toList();
    return lines.isEmpty() ? "no output" : lines.getLast();
  }

  private record Output(String stdout, String stderr) {}
}
