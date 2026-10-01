package com.cloudflow.project.domain;

/** Application type detected from well-known files at the repository root. */
public enum AppType {
  DOCKERFILE,
  JAVA_MAVEN,
  JAVA_GRADLE,
  NODE,
  PYTHON,
  UNKNOWN
}
