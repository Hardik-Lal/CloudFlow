package com.cloudflow.environment.domain;

/** Where an environment's application runs. Images are built with Docker for both targets. */
public enum DeploymentTarget {
  DOCKER("Docker"),
  KUBERNETES("Kubernetes");

  private final String label;

  DeploymentTarget(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }
}
