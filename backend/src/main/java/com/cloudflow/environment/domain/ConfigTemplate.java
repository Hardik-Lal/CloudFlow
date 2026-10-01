package com.cloudflow.environment.domain;

import com.cloudflow.project.domain.AppType;

/** Deployment configuration templates for the supported technology stacks. */
public enum ConfigTemplate {
  JAVA("Java", "Runnable JAR built with Maven or Gradle (e.g. Spring Boot)"),
  NODE("Node.js", "Node.js application started with an npm script"),
  PYTHON("Python", "Python application with dependencies in requirements.txt"),
  DOCKER("Dockerfile", "Built with the repository's own Dockerfile");

  private final String label;
  private final String description;

  ConfigTemplate(String label, String description) {
    this.label = label;
    this.description = description;
  }

  public String label() {
    return label;
  }

  public String description() {
    return description;
  }

  /** The template that matches a detected application type. */
  public static ConfigTemplate recommendedFor(AppType appType) {
    return switch (appType) {
      case JAVA_MAVEN, JAVA_GRADLE -> JAVA;
      case NODE -> NODE;
      case PYTHON -> PYTHON;
      case DOCKERFILE, UNKNOWN -> DOCKER;
    };
  }

  /** Whether this template can build an application of the given type. */
  public boolean supports(AppType appType) {
    return switch (this) {
      case JAVA -> appType == AppType.JAVA_MAVEN || appType == AppType.JAVA_GRADLE;
      case NODE -> appType == AppType.NODE;
      case PYTHON -> appType == AppType.PYTHON;
      case DOCKER -> appType == AppType.DOCKERFILE;
    };
  }
}
