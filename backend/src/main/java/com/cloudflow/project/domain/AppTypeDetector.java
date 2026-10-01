package com.cloudflow.project.domain;

import java.util.Set;

/**
 * Detects the application type from root file names. A repository's own Dockerfile wins because it
 * describes exactly how the author wants the app built; otherwise the build manifest decides.
 */
public final class AppTypeDetector {

  private AppTypeDetector() {}

  public static AppType detect(Set<String> rootFileNames) {
    if (rootFileNames.contains("Dockerfile")) {
      return AppType.DOCKERFILE;
    }
    if (rootFileNames.contains("pom.xml")) {
      return AppType.JAVA_MAVEN;
    }
    if (rootFileNames.contains("build.gradle") || rootFileNames.contains("build.gradle.kts")) {
      return AppType.JAVA_GRADLE;
    }
    if (rootFileNames.contains("package.json")) {
      return AppType.NODE;
    }
    if (rootFileNames.contains("requirements.txt") || rootFileNames.contains("pyproject.toml")) {
      return AppType.PYTHON;
    }
    return AppType.UNKNOWN;
  }
}
