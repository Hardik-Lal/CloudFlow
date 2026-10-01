package com.cloudflow.deployment.engine;

import com.cloudflow.deployment.config.DeploymentProperties;
import com.cloudflow.deployment.config.ScanProperties;
import com.cloudflow.deployment.domain.Deployment;
import com.cloudflow.deployment.domain.DeploymentStatus;
import com.cloudflow.deployment.domain.LogPhase;
import com.cloudflow.deployment.domain.ScanStatus;
import com.cloudflow.deployment.service.DeploymentStateService;
import com.cloudflow.environment.domain.ConfigTemplate;
import com.cloudflow.environment.domain.DeploymentSettings;
import com.cloudflow.environment.domain.DeploymentTarget;
import com.cloudflow.environment.dto.EnvironmentSnapshot;
import com.cloudflow.environment.dto.ValidationIssue;
import com.cloudflow.environment.dto.ValidationResult;
import com.cloudflow.environment.service.DeploymentConfigService;
import com.cloudflow.environment.service.EnvironmentLookupService;
import com.cloudflow.environment.service.VariableService;
import com.cloudflow.organization.service.OrganizationLookupService;
import com.cloudflow.project.domain.AppType;
import com.cloudflow.project.domain.AppTypeDetector;
import com.cloudflow.project.dto.ProjectSnapshot;
import com.cloudflow.project.service.ProjectLookupService;
import com.cloudflow.storage.domain.ArtifactKind;
import com.cloudflow.storage.service.ArtifactService;
import com.cloudflow.storage.service.ArtifactUpload;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Executes one deployment: fetch source, build and push the image, start the container, wait for it
 * to become healthy, then switch traffic by retiring the previous container.
 *
 * <p>Failure handling: if anything fails after the new container was started, the new container is
 * removed and the previous deployment keeps serving. The previous container is only stopped once
 * the new one is healthy.
 */
@Component
public class DeploymentRunner {

  private static final Logger log = LoggerFactory.getLogger(DeploymentRunner.class);
  private static final int FAILURE_LOG_LINES = 50;
  private static final String GENERATED_DOCKERFILE = ".cloudflow.Dockerfile";
  private static final int MAX_LOGGED_FINDINGS = 25;

  private final DeploymentStateService state;
  private final ContainerRuntime runtime;
  private final SourceFetcher sourceFetcher;
  private final DockerfileGenerator dockerfileGenerator;
  private final HealthChecker healthChecker;
  private final DeploymentLogSinkFactory logSinks;
  private final ProjectLookupService projects;
  private final EnvironmentLookupService environments;
  private final OrganizationLookupService organizations;
  private final DeploymentConfigService configs;
  private final VariableService variables;
  private final EnvironmentSecretMasker secretMasker;
  private final DeploymentEndpoints endpoints;
  private final ImageScanner imageScanner;
  private final ScanProperties scanProperties;
  private final DeploymentProperties properties;
  private final ArtifactService artifacts;
  private final JsonMapper jsonMapper;

  public DeploymentRunner(
      DeploymentStateService state,
      ContainerRuntime runtime,
      SourceFetcher sourceFetcher,
      DockerfileGenerator dockerfileGenerator,
      HealthChecker healthChecker,
      DeploymentLogSinkFactory logSinks,
      ProjectLookupService projects,
      EnvironmentLookupService environments,
      OrganizationLookupService organizations,
      DeploymentConfigService configs,
      VariableService variables,
      EnvironmentSecretMasker secretMasker,
      DeploymentEndpoints endpoints,
      ImageScanner imageScanner,
      ScanProperties scanProperties,
      DeploymentProperties properties,
      ArtifactService artifacts,
      JsonMapper jsonMapper) {
    this.artifacts = artifacts;
    this.jsonMapper = jsonMapper;
    this.state = state;
    this.runtime = runtime;
    this.sourceFetcher = sourceFetcher;
    this.dockerfileGenerator = dockerfileGenerator;
    this.healthChecker = healthChecker;
    this.logSinks = logSinks;
    this.projects = projects;
    this.environments = environments;
    this.organizations = organizations;
    this.configs = configs;
    this.variables = variables;
    this.secretMasker = secretMasker;
    this.endpoints = endpoints;
    this.imageScanner = imageScanner;
    this.scanProperties = scanProperties;
    this.properties = properties;
  }

  public void run(UUID deploymentId) {
    Optional<Deployment> started = state.begin(deploymentId);
    if (started.isEmpty()) {
      log.info("Deployment {} is no longer queued; skipping", deploymentId);
      return;
    }
    Deployment deployment = started.get();
    Path workDirectory = properties.workDirectory().resolve(deploymentId.toString());
    DeploymentLogSink logs = logSinks.create(deploymentId, SecretMasker.none());
    String newContainerId = null;

    try {
      EnvironmentSnapshot environment = environments.snapshot(deployment.getEnvironmentId());
      ProjectSnapshot project = projects.snapshot(deployment.getProjectId());
      DeploymentSettings settings = configs.settings(environment.id());
      Map<String, String> userVariables = variables.resolveForDeployment(environment.id());
      logs = logSinks.create(deploymentId, secretMasker.forEnvironment(environment.id()));

      String imageTag;
      if (deployment.isRollback()) {
        imageTag = prepareRollbackImage(deployment, logs);
      } else {
        imageTag = buildImage(deployment, environment, project, settings, workDirectory, logs);
      }

      transition(deploymentId, DeploymentStatus.BUILDING, DeploymentStatus.DEPLOYING);
      logs.info(LogPhase.DEPLOY, "Starting " + imageTag + " on " + settings.target().label());
      RunningContainer container =
          runtime.run(
              containerSpec(deployment, environment, project, settings, imageTag, userVariables));
      newContainerId = container.id();
      state.recordContainer(
          deploymentId, settings.target(), container.id(), container.name(), container.hostPort());
      logs.info(
          LogPhase.DEPLOY,
          "Container "
              + container.name()
              + " started"
              + (container.hostPort() == null ? "" : " (port " + container.hostPort() + ")"));

      transition(deploymentId, DeploymentStatus.DEPLOYING, DeploymentStatus.HEALTH_CHECK);
      awaitHealthy(container, settings, logs);

      Optional<String> previousContainer = state.activate(deploymentId);
      logs.info(LogPhase.DEPLOY, "Deployment is live");
      previousContainer.ifPresent(id -> retirePreviousContainer(id, deploymentId));
    } catch (DeploymentFailure | ContainerRuntime.RuntimeFailure e) {
      handleFailure(deploymentId, newContainerId, e.getMessage(), logs);
    } catch (DeploymentCancelled e) {
      logs.warn(LogPhase.DEPLOY, "Deployment was cancelled");
      removeQuietly(newContainerId);
    } catch (RuntimeException e) {
      log.error("Deployment {} failed unexpectedly", deploymentId, e);
      handleFailure(deploymentId, newContainerId, "Unexpected error: " + e.getMessage(), logs);
    } finally {
      deleteRecursively(workDirectory);
    }
  }

  private String buildImage(
      Deployment deployment,
      EnvironmentSnapshot environment,
      ProjectSnapshot project,
      DeploymentSettings settings,
      Path workDirectory,
      DeploymentLogSink logs) {
    ValidationResult validation = configs.validateForDeployment(environment.id());
    validation.warnings().forEach(issue -> logs.warn(LogPhase.SOURCE, describe(issue)));
    if (!validation.valid()) {
      validation.errors().forEach(issue -> logs.error(LogPhase.SOURCE, describe(issue)));
      throw new DeploymentFailure("The deployment configuration is invalid");
    }

    String ref =
        deployment.getCommitSha() != null ? deployment.getCommitSha() : deployment.getBranch();
    logs.info(LogPhase.SOURCE, "Fetching " + project.repositoryFullName() + " at " + ref);
    SourceCheckout source;
    try {
      Files.createDirectories(workDirectory);
      source = sourceFetcher.fetch(deployment.getTriggeredBy(), project, ref, workDirectory);
    } catch (IOException | RuntimeException e) {
      throw new DeploymentFailure("Could not retrieve the source code: " + e.getMessage());
    }
    AppType appType = AppTypeDetector.detect(source.rootFileNames());
    state.recordSource(deployment.getId(), source.commitSha(), source.commitMessage(), appType);
    logs.info(
        LogPhase.SOURCE,
        "Checked out " + source.commitSha().substring(0, 7) + " (detected " + appType + ")");
    ensureNotCancelled(deployment.getId());

    Path dockerfile = dockerfileFor(settings, appType, source, logs);
    archiveBuildArtifact(
        deployment, "Dockerfile", "text/plain; charset=utf-8", readQuietly(dockerfile));
    String imageTag = imageTag(project, environment, source.commitSha(), deployment.getId());
    logs.info(LogPhase.BUILD, "Building image " + imageTag);
    String imageId =
        runtime.build(source.directory(), dockerfile, imageTag, logs.lines(LogPhase.BUILD));
    state.recordImage(deployment.getId(), imageTag, imageId);
    logs.info(LogPhase.BUILD, "Built image " + shortId(imageId));
    ensureNotCancelled(deployment.getId());

    scanImage(deployment, imageTag, logs);
    ensureNotCancelled(deployment.getId());

    // Kubernetes nodes pull from the registry, so their images are always pushed.
    if (properties.pushImages() || settings.target() == DeploymentTarget.KUBERNETES) {
      logs.info(LogPhase.PUSH, "Pushing " + imageTag + " to " + properties.registry());
      runtime.push(imageTag, logs.lines(LogPhase.PUSH));
    }
    return imageTag;
  }

  private void scanImage(Deployment deployment, String imageTag, DeploymentLogSink logs) {
    UUID deploymentId = deployment.getId();
    if (!scanProperties.enabled()) {
      state.recordScan(deploymentId, ScanStatus.SKIPPED, null, null);
      return;
    }
    logs.info(
        LogPhase.SCAN, "Scanning " + imageTag + " for HIGH and CRITICAL vulnerabilities (Trivy)");
    Optional<ScanResult> result = imageScanner.scan(imageTag, logs.lines(LogPhase.SCAN));
    if (result.isEmpty()) {
      state.recordScan(deploymentId, ScanStatus.ERROR, null, null);
      logs.warn(LogPhase.SCAN, "Continuing without vulnerability scan results");
      return;
    }
    ScanResult scan = result.get();
    archiveBuildArtifact(
        deployment,
        "vulnerability-report.json",
        "application/json",
        jsonMapper.writeValueAsString(scan));
    ScanStatus status = ScanPolicy.evaluate(scan, scanProperties.blockOnCritical());
    state.recordScan(deploymentId, status, scan.critical(), scan.high());
    String summary = scan.critical() + " critical, " + scan.high() + " high vulnerabilities";
    if (status == ScanStatus.PASSED) {
      logs.info(LogPhase.SCAN, "No HIGH or CRITICAL vulnerabilities found");
    } else {
      logs.warn(LogPhase.SCAN, summary);
      scan.findings().stream()
          .limit(MAX_LOGGED_FINDINGS)
          .forEach(
              finding ->
                  logs.warn(
                      LogPhase.SCAN,
                      finding.severity()
                          + " "
                          + finding.id()
                          + " in "
                          + finding.packageName()
                          + " "
                          + finding.installedVersion()
                          + (finding.fixedVersion().isBlank()
                              ? " (no fix yet)"
                              : " → fixed in " + finding.fixedVersion())
                          + (finding.title().isBlank() ? "" : ": " + finding.title())));
      if (scan.findings().size() > MAX_LOGGED_FINDINGS) {
        logs.warn(
            LogPhase.SCAN, "… and " + (scan.findings().size() - MAX_LOGGED_FINDINGS) + " more");
      }
    }
    if (status == ScanStatus.BLOCKED) {
      throw new DeploymentFailure(
          "The image has " + summary + "; deployment blocked by the vulnerability policy");
    }
  }

  /** Keeps a copy of a build output in artifact storage (best effort, in the background). */
  private void archiveBuildArtifact(
      Deployment deployment, String name, String contentType, String content) {
    if (content == null) {
      return;
    }
    artifacts.storeAsync(
        () ->
            ArtifactUpload.text(
                deployment.getProjectId(),
                deployment.getEnvironmentId(),
                deployment.getId(),
                ArtifactKind.BUILD_ARTIFACT,
                name,
                contentType,
                content,
                deployment.getTriggeredBy()));
  }

  private static String readQuietly(Path file) {
    try {
      return Files.readString(file);
    } catch (IOException e) {
      return null;
    }
  }

  private String prepareRollbackImage(Deployment deployment, DeploymentLogSink logs) {
    String imageTag = deployment.getImageTag();
    logs.info(LogPhase.SOURCE, "Rolling back to image " + imageTag);
    if (!runtime.imageExists(imageTag)) {
      logs.info(LogPhase.PUSH, "Image not present locally; pulling from the registry");
      try {
        runtime.pull(imageTag, logs.lines(LogPhase.PUSH));
      } catch (ContainerRuntime.RuntimeFailure e) {
        throw new DeploymentFailure("The rollback image is no longer available: " + e.getMessage());
      }
    }
    return imageTag;
  }

  private Path dockerfileFor(
      DeploymentSettings settings, AppType appType, SourceCheckout source, DeploymentLogSink logs) {
    if (settings.template() == ConfigTemplate.DOCKER) {
      Path dockerfile = source.directory().resolve(settings.dockerfilePath()).normalize();
      if (!dockerfile.startsWith(source.directory()) || !Files.isRegularFile(dockerfile)) {
        throw new DeploymentFailure(
            "No Dockerfile found at '" + settings.dockerfilePath() + "' in the repository");
      }
      logs.info(LogPhase.BUILD, "Using the repository's " + settings.dockerfilePath());
      return dockerfile;
    }
    if (!settings.template().supports(appType)) {
      logs.warn(
          LogPhase.BUILD,
          "The "
              + settings.template().label()
              + " template does not match the detected type "
              + appType);
    }
    String content = dockerfileGenerator.generate(settings, appType);
    Path dockerfile = source.directory().resolve(GENERATED_DOCKERFILE);
    try {
      Files.writeString(dockerfile, content);
    } catch (IOException e) {
      throw new DeploymentFailure("Could not write the generated Dockerfile: " + e.getMessage());
    }
    logs.info(
        LogPhase.BUILD,
        "Generated a Dockerfile from the " + settings.template().label() + " template");
    content.lines().forEach(line -> logs.info(LogPhase.BUILD, "  " + line));
    return dockerfile;
  }

  private ContainerSpec containerSpec(
      Deployment deployment,
      EnvironmentSnapshot environment,
      ProjectSnapshot project,
      DeploymentSettings settings,
      String imageTag,
      Map<String, String> userVariables) {
    String environmentName = environment.type().name().toLowerCase(Locale.ROOT);
    Map<String, String> containerEnvironment = new LinkedHashMap<>();
    containerEnvironment.put("PORT", String.valueOf(settings.containerPort()));
    // User variables may override PORT; CLOUDFLOW_* keys are reserved and always set here.
    containerEnvironment.putAll(userVariables);
    containerEnvironment.put("CLOUDFLOW_ENVIRONMENT", environmentName);
    containerEnvironment.put("CLOUDFLOW_DEPLOYMENT_ID", deployment.getId().toString());
    containerEnvironment.put("CLOUDFLOW_DATA_DIR", DockerfileGenerator.DATA_DIRECTORY);

    return new ContainerSpec(
        settings.target(),
        "cf-" + project.slug() + "-" + environmentName + "-" + shortUuid(deployment.getId()),
        imageTag,
        containerEnvironment,
        settings.containerPort(),
        properties.appNetwork(),
        "cf-data-" + environment.id(),
        DockerfileGenerator.DATA_DIRECTORY,
        settings.cpuLimit(),
        settings.memoryLimitMb(),
        Map.of(
            "cloudflow.managed", "true",
            "cloudflow.project", project.id().toString(),
            "cloudflow.environment", environment.id().toString(),
            "cloudflow.deployment", deployment.getId().toString()));
  }

  private void awaitHealthy(
      RunningContainer container, DeploymentSettings settings, DeploymentLogSink logs) {
    URI url = healthCheckUrl(container, settings);
    Instant deadline = Instant.now().plus(properties.healthCheckTimeout());
    logs.info(LogPhase.HEALTH_CHECK, "Waiting for " + url + " to respond");
    HealthProbe lastProbe = null;
    int attempts = 0;
    while (true) {
      ContainerState containerState = runtime.inspect(container.id());
      if (!containerState.exists()
          || !containerState.running()
          || containerState.restartCount() > 0) {
        throw new DeploymentFailure(
            "The container stopped during startup"
                + (containerState.exitCode() == null
                    ? ""
                    : " (exit code " + containerState.exitCode() + ")")
                + (containerState.restartCount() > 0 ? " and was restarted" : ""));
      }
      lastProbe = healthChecker.probe(url);
      attempts++;
      if (lastProbe.healthy()) {
        logs.info(
            LogPhase.HEALTH_CHECK,
            "Healthy: HTTP "
                + lastProbe.statusCode()
                + " in "
                + lastProbe.responseTimeMs()
                + " ms"
                + " after "
                + attempts
                + " attempt(s)");
        return;
      }
      if (attempts == 1 || attempts % 5 == 0) {
        logs.info(LogPhase.HEALTH_CHECK, "Not ready yet: " + describe(lastProbe));
      }
      if (Instant.now().isAfter(deadline)) {
        throw new DeploymentFailure(
            "Health check did not pass within "
                + properties.healthCheckTimeout().toSeconds()
                + "s (last result: "
                + describe(lastProbe)
                + ")");
      }
      sleep(properties.healthCheckInterval());
    }
  }

  private URI healthCheckUrl(RunningContainer container, DeploymentSettings settings) {
    URI url =
        endpoints.probeUrl(
            settings.target(),
            container.name(),
            container.hostPort(),
            settings.containerPort(),
            settings.healthCheckPath());
    if (url == null) {
      throw new DeploymentFailure("The application port is not reachable for health checks");
    }
    return url;
  }

  private void handleFailure(
      UUID deploymentId, String containerId, String reason, DeploymentLogSink logs) {
    logs.error(LogPhase.DEPLOY, reason);
    if (containerId != null) {
      List<String> tail = safeTail(containerId);
      if (!tail.isEmpty()) {
        logs.info(LogPhase.RUNTIME, "Last " + tail.size() + " lines of container output:");
        tail.forEach(line -> logs.info(LogPhase.RUNTIME, line));
      }
      removeQuietly(containerId);
      logs.info(
          LogPhase.DEPLOY, "Removed the failed container; the previous deployment keeps serving");
    }
    state.fail(deploymentId, reason);
  }

  private void retirePreviousContainer(String containerId, UUID deploymentId) {
    if (containerId == null) {
      return;
    }
    try {
      runtime.remove(containerId);
    } catch (RuntimeException e) {
      log.warn(
          "Could not remove container {} replaced by deployment {}", containerId, deploymentId, e);
    }
  }

  private List<String> safeTail(String containerId) {
    try {
      return runtime.tailLogs(containerId, FAILURE_LOG_LINES);
    } catch (RuntimeException e) {
      return List.of();
    }
  }

  private void removeQuietly(String containerId) {
    if (containerId == null) {
      return;
    }
    try {
      runtime.remove(containerId);
    } catch (RuntimeException e) {
      log.warn("Could not remove container {}", containerId, e);
    }
  }

  private void transition(UUID deploymentId, DeploymentStatus from, DeploymentStatus to) {
    if (!state.advance(deploymentId, from, to)) {
      throw new DeploymentCancelled();
    }
  }

  private void ensureNotCancelled(UUID deploymentId) {
    if (state.status(deploymentId) == DeploymentStatus.CANCELLED) {
      throw new DeploymentCancelled();
    }
  }

  private String imageTag(
      ProjectSnapshot project, EnvironmentSnapshot environment, String sha, UUID deploymentId) {
    return properties.registry()
        + "/"
        + organizations.slug(project.organizationId())
        + "/"
        + project.slug()
        + ":"
        + environment.type().name().toLowerCase(Locale.ROOT)
        + "-"
        + sha.substring(0, 7)
        + "-"
        + shortUuid(deploymentId);
  }

  private static String describe(ValidationIssue issue) {
    return issue.field() + ": " + issue.message();
  }

  private static String describe(HealthProbe probe) {
    return probe.statusCode() != null ? "HTTP " + probe.statusCode() : probe.error();
  }

  private static String shortUuid(UUID id) {
    return id.toString().substring(0, 8);
  }

  private static String shortId(String imageId) {
    String id = imageId.startsWith("sha256:") ? imageId.substring(7) : imageId;
    return id.substring(0, Math.min(12, id.length()));
  }

  private static void sleep(Duration duration) {
    try {
      Thread.sleep(duration);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new DeploymentFailure("The deployment was interrupted");
    }
  }

  private static void deleteRecursively(Path directory) {
    if (!Files.exists(directory)) {
      return;
    }
    try (Stream<Path> paths = Files.walk(directory)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).collect(Collectors.toList())) {
        Files.deleteIfExists(path);
      }
    } catch (IOException e) {
      log.warn("Could not delete work directory {}", directory, e);
    }
  }

  /** A failure that ends the deployment with a user-facing reason. */
  static class DeploymentFailure extends RuntimeException {
    DeploymentFailure(String message) {
      super(message);
    }
  }

  /** The deployment was cancelled while it was running. */
  static class DeploymentCancelled extends RuntimeException {
    DeploymentCancelled() {
      super("Cancelled");
    }
  }
}
