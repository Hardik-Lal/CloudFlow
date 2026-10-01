package com.cloudflow.environment.domain;

import com.cloudflow.project.domain.AppType;
import java.math.BigDecimal;

/** The editable values of a deployment configuration. */
public record DeploymentSettings(
    ConfigTemplate template,
    String runtimeVersion,
    String buildCommand,
    String startCommand,
    String dockerfilePath,
    int containerPort,
    String healthCheckPath,
    BigDecimal cpuLimit,
    Integer memoryLimitMb,
    DeploymentTarget target) {

  public DeploymentSettings {
    if (target == null) {
      target = DeploymentTarget.DOCKER;
    }
  }

  /** Template defaults; Java build commands depend on the detected build tool. */
  public static DeploymentSettings defaults(ConfigTemplate template, AppType appType) {
    return switch (template) {
      case JAVA ->
          new DeploymentSettings(
              template,
              "21",
              appType == AppType.JAVA_GRADLE
                  ? "gradle build -x test --no-daemon"
                  : "mvn -B -DskipTests package",
              "java -jar app.jar",
              "Dockerfile",
              8080,
              "/",
              new BigDecimal("1.00"),
              512,
              DeploymentTarget.DOCKER);
      case NODE ->
          new DeploymentSettings(
              template,
              "22",
              "npm ci && npm run build --if-present",
              "npm start",
              "Dockerfile",
              3000,
              "/",
              new BigDecimal("0.50"),
              512,
              DeploymentTarget.DOCKER);
      case PYTHON ->
          new DeploymentSettings(
              template,
              "3.12",
              "pip install --no-cache-dir -r requirements.txt",
              "python app.py",
              "Dockerfile",
              8000,
              "/",
              new BigDecimal("0.50"),
              512,
              DeploymentTarget.DOCKER);
      case DOCKER ->
          new DeploymentSettings(
              template,
              null,
              null,
              null,
              "Dockerfile",
              8080,
              "/",
              new BigDecimal("1.00"),
              512,
              DeploymentTarget.DOCKER);
    };
  }
}
