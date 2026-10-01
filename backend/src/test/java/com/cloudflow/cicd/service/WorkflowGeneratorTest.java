package com.cloudflow.cicd.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.cloudflow.cicd.service.WorkflowGenerator.WorkflowSpec;
import com.cloudflow.deployment.engine.DockerfileGenerator;
import com.cloudflow.environment.domain.ConfigTemplate;
import com.cloudflow.environment.domain.DeploymentSettings;
import com.cloudflow.environment.domain.EnvironmentType;
import com.cloudflow.project.domain.AppType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.yaml.snakeyaml.Yaml;

class WorkflowGeneratorTest {

  private final WorkflowGenerator generator = new WorkflowGenerator();
  private final DockerfileGenerator dockerfiles = new DockerfileGenerator();

  @ParameterizedTest
  @EnumSource(ConfigTemplate.class)
  void everyTemplateProducesTheStandardPipeline(ConfigTemplate template) {
    Map<String, Object> workflow = parse(generate(template, EnvironmentType.STAGING, "release"));

    Map<String, Object> jobs = map(workflow.get("jobs"));
    assertThat(jobs.keySet()).containsExactly("test", "build", "docker", "deploy", "health-check");
    assertThat(map(jobs.get("build")).get("needs")).isEqualTo("test");
    assertThat(map(jobs.get("docker")).get("needs")).isEqualTo("build");
    assertThat(map(jobs.get("deploy")).get("needs")).isEqualTo("docker");
    assertThat(map(jobs.get("health-check")).get("needs")).isEqualTo("deploy");

    // SnakeYAML (YAML 1.1) reads the "on" key as boolean true.
    Map<String, Object> triggers = map(workflow.get(Boolean.TRUE));
    assertThat(map(triggers.get("push")).get("branches")).isEqualTo(List.of("release"));
    assertThat(map(workflow.get("permissions"))).containsEntry("packages", "write");
  }

  @Test
  void templateBuildsWriteTheCloudFlowDockerfileAndPushToGhcr() {
    String yaml = generate(ConfigTemplate.JAVA, EnvironmentType.DEVELOPMENT, "develop");
    List<Map<String, Object>> steps = steps(parse(yaml), "docker");

    String writeStep = (String) steps.get(1).get("run");
    assertThat(writeStep)
        .startsWith("cat > .cloudflow.Dockerfile <<'CLOUDFLOW_DOCKERFILE'")
        .contains("FROM maven:3.9-eclipse-temurin-21 AS build")
        .endsWith("CLOUDFLOW_DOCKERFILE\n");
    Map<String, Object> build = map(steps.get(4).get("with"));
    assertThat(build).containsEntry("file", ".cloudflow.Dockerfile").containsEntry("push", true);
    assertThat((String) build.get("tags")).contains("${{ env.IMAGE }}:${{ github.sha }}");
  }

  @Test
  void dockerTemplateUsesTheRepositoryDockerfile() {
    String yaml = generate(ConfigTemplate.DOCKER, EnvironmentType.PRODUCTION, "main");

    assertThat(yaml).doesNotContain("CLOUDFLOW_DOCKERFILE");
    List<Map<String, Object>> steps = steps(parse(yaml), "docker");
    assertThat(map(steps.get(3).get("with"))).containsEntry("file", "Dockerfile");
  }

  @Test
  void deployJobCallsCloudFlowWithTheEnvironmentToken() {
    String yaml = generate(ConfigTemplate.NODE, EnvironmentType.PRODUCTION, "main");
    Map<String, Object> deployStep = steps(parse(yaml), "deploy").getFirst();

    assertThat(map(deployStep.get("env")))
        .containsEntry("CLOUDFLOW_URL", "${{ secrets.CLOUDFLOW_URL }}")
        .containsEntry(
            "CLOUDFLOW_DEPLOY_TOKEN", "${{ secrets.CLOUDFLOW_DEPLOY_TOKEN_PRODUCTION }}");
    assertThat((String) deployStep.get("run"))
        .contains("/api/v1/pipeline-hooks/deploy")
        .contains("X-CloudFlow-Deploy-Token: $CLOUDFLOW_DEPLOY_TOKEN")
        .contains("\\\"commitSha\\\":\\\"$GITHUB_SHA\\\"");
    assertThat((String) steps(parse(yaml), "health-check").getFirst().get("run"))
        .contains("SUCCEEDED)")
        .contains("FAILED|CANCELLED)");
  }

  @Test
  void userCommandsCannotInjectGithubExpressions() {
    DeploymentSettings settings =
        new DeploymentSettings(
            ConfigTemplate.NODE,
            "22",
            "echo ${{ secrets.CLOUDFLOW_URL }}",
            "npm start",
            "Dockerfile",
            3000,
            "/",
            BigDecimal.ONE,
            512,
            null);
    String yaml =
        generator.generate(
            new WorkflowSpec(
                "app", EnvironmentType.DEVELOPMENT, "main", settings, AppType.NODE, null));

    assertThat((String) steps(parse(yaml), "build").get(2).get("run"))
        .isEqualTo("echo ${{ '${{' }} secrets.CLOUDFLOW_URL }}\n");
  }

  private String generate(ConfigTemplate template, EnvironmentType environment, String branch) {
    AppType appType =
        switch (template) {
          case JAVA -> AppType.JAVA_MAVEN;
          case NODE -> AppType.NODE;
          case PYTHON -> AppType.PYTHON;
          case DOCKER -> AppType.DOCKERFILE;
        };
    DeploymentSettings settings = DeploymentSettings.defaults(template, appType);
    String dockerfile =
        template == ConfigTemplate.DOCKER ? null : dockerfiles.generate(settings, appType);
    return generator.generate(
        new WorkflowSpec("demo", environment, branch, settings, appType, dockerfile));
  }

  private static Map<String, Object> parse(String yaml) {
    return new Yaml().load(yaml);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object value) {
    return (Map<String, Object>) value;
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> steps(Map<String, Object> workflow, String job) {
    return (List<Map<String, Object>>) map(map(workflow.get("jobs")).get(job)).get("steps");
  }
}
