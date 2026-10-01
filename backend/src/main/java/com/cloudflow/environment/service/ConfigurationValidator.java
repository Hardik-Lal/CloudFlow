package com.cloudflow.environment.service;

import com.cloudflow.environment.domain.ConfigTemplate;
import com.cloudflow.environment.domain.DeploymentSettings;
import com.cloudflow.environment.dto.ValidationIssue;
import com.cloudflow.environment.dto.ValidationResult;
import com.cloudflow.project.domain.AppType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Checks that an environment can be deployed with its current configuration. Runs on demand and
 * before every deployment.
 */
@Component
public class ConfigurationValidator {

  static final BigDecimal MIN_CPU = new BigDecimal("0.10");
  static final BigDecimal MAX_CPU = new BigDecimal("8.00");
  static final int MIN_MEMORY_MB = 128;
  static final int MAX_MEMORY_MB = 16_384;
  static final int FIRST_UNPRIVILEGED_PORT = 1024;

  private static final Pattern SAFE_RELATIVE_PATH =
      Pattern.compile("^(?!/)(?!.*(^|/)\\.\\.(/|$))[A-Za-z0-9._/-]+$");
  private static final Pattern HEALTH_PATH = Pattern.compile("^/[A-Za-z0-9._~/?=&%-]*$");
  private static final Pattern RUNTIME_VERSION = Pattern.compile("^[0-9]+(\\.[0-9]+){0,2}$");

  private final DeploymentTargetAvailability targets;

  public ConfigurationValidator(DeploymentTargetAvailability targets) {
    this.targets = targets;
  }

  public ValidationResult validate(
      DeploymentSettings settings, AppType appType, String branch, Set<String> knownBranches) {
    List<ValidationIssue> errors = new ArrayList<>();
    List<ValidationIssue> warnings = new ArrayList<>();
    ConfigTemplate template = settings.template();

    if (!knownBranches.isEmpty() && !knownBranches.contains(branch)) {
      errors.add(
          new ValidationIssue(
              "branch",
              "Branch '"
                  + branch
                  + "' does not exist in the repository; sync the project or pick"
                  + " another branch"));
    }

    if (!targets.isAvailable(settings.target())) {
      errors.add(
          new ValidationIssue(
              "target",
              settings.target().label()
                  + " is not configured on this CloudFlow installation; choose another target"));
    }

    if (template == ConfigTemplate.DOCKER) {
      if (isBlank(settings.dockerfilePath())
          || !SAFE_RELATIVE_PATH.matcher(settings.dockerfilePath()).matches()) {
        errors.add(
            new ValidationIssue("dockerfilePath", "Must be a relative path inside the repository"));
      }
    } else {
      if (isBlank(settings.startCommand())) {
        errors.add(new ValidationIssue("startCommand", "A start command is required"));
      }
      if (template == ConfigTemplate.JAVA && isBlank(settings.buildCommand())) {
        errors.add(new ValidationIssue("buildCommand", "Java applications need a build command"));
      }
      if (isBlank(settings.runtimeVersion())
          || !RUNTIME_VERSION.matcher(settings.runtimeVersion()).matches()) {
        errors.add(
            new ValidationIssue("runtimeVersion", "Must be a version number such as 21 or 3.12"));
      }
    }
    checkSingleLine("buildCommand", settings.buildCommand(), errors);
    checkSingleLine("startCommand", settings.startCommand(), errors);

    if (appType != AppType.UNKNOWN && !template.supports(appType)) {
      warnings.add(
          new ValidationIssue(
              "template",
              "The "
                  + template.label()
                  + " template does not match the detected application type"
                  + " ("
                  + appType
                  + "); the build may fail"));
    }
    if (appType == AppType.UNKNOWN && template != ConfigTemplate.DOCKER) {
      warnings.add(
          new ValidationIssue(
              "template", "The application type could not be detected from the repository"));
    }

    if (settings.containerPort() < 1 || settings.containerPort() > 65_535) {
      errors.add(new ValidationIssue("containerPort", "Must be between 1 and 65535"));
    } else if (settings.containerPort() < FIRST_UNPRIVILEGED_PORT) {
      warnings.add(
          new ValidationIssue(
              "containerPort", "Ports below 1024 need root inside the container; prefer 8080"));
    }

    if (isBlank(settings.healthCheckPath())
        || !HEALTH_PATH.matcher(settings.healthCheckPath()).matches()) {
      errors.add(new ValidationIssue("healthCheckPath", "Must be a URL path starting with '/'"));
    }

    if (settings.cpuLimit() == null) {
      warnings.add(new ValidationIssue("cpuLimit", "No CPU limit; the container can use all CPUs"));
    } else if (settings.cpuLimit().compareTo(MIN_CPU) < 0
        || settings.cpuLimit().compareTo(MAX_CPU) > 0) {
      errors.add(new ValidationIssue("cpuLimit", "Must be between 0.1 and 8 CPUs"));
    }

    if (settings.memoryLimitMb() == null) {
      warnings.add(new ValidationIssue("memoryLimitMb", "No memory limit is set"));
    } else if (settings.memoryLimitMb() < MIN_MEMORY_MB
        || settings.memoryLimitMb() > MAX_MEMORY_MB) {
      errors.add(new ValidationIssue("memoryLimitMb", "Must be between 128 and 16384 MB"));
    }

    return ValidationResult.of(errors, warnings);
  }

  /** Commands are embedded in generated Dockerfiles, so they must stay on a single line. */
  private static void checkSingleLine(String field, String value, List<ValidationIssue> errors) {
    if (value != null && (value.contains("\n") || value.contains("\r"))) {
      errors.add(new ValidationIssue(field, "Must be a single line"));
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
