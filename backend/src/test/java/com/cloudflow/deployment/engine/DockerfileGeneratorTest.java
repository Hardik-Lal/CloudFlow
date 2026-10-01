package com.cloudflow.deployment.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cloudflow.environment.domain.ConfigTemplate;
import com.cloudflow.environment.domain.DeploymentSettings;
import com.cloudflow.project.domain.AppType;
import org.junit.jupiter.api.Test;

class DockerfileGeneratorTest {

  private final DockerfileGenerator generator = new DockerfileGenerator();

  @Test
  void javaMavenUsesMavenBuilderAndSlimJreRuntimeAsNonRoot() {
    String dockerfile =
        generator.generate(
            DeploymentSettings.defaults(ConfigTemplate.JAVA, AppType.JAVA_MAVEN),
            AppType.JAVA_MAVEN);

    assertThat(dockerfile)
        .contains("FROM maven:3.9-eclipse-temurin-21 AS build")
        .contains("RUN mvn -B -DskipTests package")
        .contains("ls target/*.jar")
        .contains("FROM eclipse-temurin:21-jre")
        .contains("USER app")
        .contains("EXPOSE 8080")
        .contains("CMD [\"sh\", \"-c\", \"java -jar app.jar\"]");
  }

  @Test
  void javaGradleUsesGradleBuilderAndBuildLibs() {
    String dockerfile =
        generator.generate(
            DeploymentSettings.defaults(ConfigTemplate.JAVA, AppType.JAVA_GRADLE),
            AppType.JAVA_GRADLE);

    assertThat(dockerfile).contains("FROM gradle:jdk21 AS build").contains("ls build/libs/*.jar");
  }

  @Test
  void nodeAndPythonRunAsNonRootUsers() {
    assertThat(
            generator.generate(
                DeploymentSettings.defaults(ConfigTemplate.NODE, AppType.NODE), AppType.NODE))
        .contains("FROM node:22-alpine")
        .contains("USER node")
        .contains("EXPOSE 3000");
    assertThat(
            generator.generate(
                DeploymentSettings.defaults(ConfigTemplate.PYTHON, AppType.PYTHON), AppType.PYTHON))
        .contains("FROM python:3.12-slim")
        .contains("USER app")
        .contains("CMD [\"sh\", \"-c\", \"python app.py\"]");
  }

  @Test
  void escapesStartCommandsForExecForm() {
    assertThat(DockerfileGenerator.jsonString("node \"server.js\" \\ --flag"))
        .isEqualTo("\"node \\\"server.js\\\" \\\\ --flag\"");
  }

  @Test
  void dockerTemplateHasNoGeneratedDockerfile() {
    assertThatThrownBy(
            () ->
                generator.generate(
                    DeploymentSettings.defaults(ConfigTemplate.DOCKER, AppType.DOCKERFILE),
                    AppType.DOCKERFILE))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
