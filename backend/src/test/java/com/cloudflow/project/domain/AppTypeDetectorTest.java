package com.cloudflow.project.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class AppTypeDetectorTest {

  @Test
  void dockerfileTakesPrecedenceOverBuildManifests() {
    assertThat(AppTypeDetector.detect(Set.of("Dockerfile", "pom.xml", "package.json")))
        .isEqualTo(AppType.DOCKERFILE);
  }

  @Test
  void detectsBuildManifests() {
    assertThat(AppTypeDetector.detect(Set.of("pom.xml", "src"))).isEqualTo(AppType.JAVA_MAVEN);
    assertThat(AppTypeDetector.detect(Set.of("build.gradle"))).isEqualTo(AppType.JAVA_GRADLE);
    assertThat(AppTypeDetector.detect(Set.of("build.gradle.kts"))).isEqualTo(AppType.JAVA_GRADLE);
    assertThat(AppTypeDetector.detect(Set.of("package.json", "README.md"))).isEqualTo(AppType.NODE);
    assertThat(AppTypeDetector.detect(Set.of("requirements.txt"))).isEqualTo(AppType.PYTHON);
    assertThat(AppTypeDetector.detect(Set.of("pyproject.toml"))).isEqualTo(AppType.PYTHON);
  }

  @Test
  void fallsBackToUnknown() {
    assertThat(AppTypeDetector.detect(Set.of("README.md", "docs"))).isEqualTo(AppType.UNKNOWN);
    assertThat(AppTypeDetector.detect(Set.of())).isEqualTo(AppType.UNKNOWN);
  }
}
