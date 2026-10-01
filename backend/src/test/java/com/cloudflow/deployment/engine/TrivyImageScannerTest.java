package com.cloudflow.deployment.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.cloudflow.deployment.config.ScanProperties;
import com.github.dockerjava.api.DockerClient;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class TrivyImageScannerTest {

  private final TrivyImageScanner scanner =
      new TrivyImageScanner(
          mock(DockerClient.class),
          new ScanProperties(
              true,
              "aquasec/trivy:0.74.0",
              false,
              Duration.ofMinutes(10),
              "cache",
              "/var/run/docker.sock"),
          JsonMapper.builder().build());

  @Test
  void parsesTrivyJsonReports() {
    String report =
        """
        {"SchemaVersion": 2, "ArtifactName": "app:1", "Results": [
          {"Target": "app:1 (debian 12)", "Class": "os-pkgs", "Vulnerabilities": [
            {"VulnerabilityID": "CVE-2026-1111", "PkgName": "libssl3", "InstalledVersion": "3.0.1",
             "FixedVersion": "3.0.9", "Severity": "HIGH", "Title": "Buffer overflow"},
            {"VulnerabilityID": "CVE-2026-0001", "PkgName": "zlib", "InstalledVersion": "1.2",
             "Severity": "CRITICAL", "Title": "Heap corruption"}]},
          {"Target": "requirements.txt", "Class": "lang-pkgs"}]}
        """;

    ScanResult result = scanner.parse(report);

    assertThat(result.critical()).isEqualTo(1);
    assertThat(result.high()).isEqualTo(1);
    assertThat(result.findings().getFirst().id()).isEqualTo("CVE-2026-0001");
    assertThat(result.findings().getFirst().fixedVersion()).isEmpty();
  }

  @Test
  void anImageWithoutFindingsIsClean() {
    assertThat(scanner.parse("{\"Results\": [{\"Target\": \"x\"}]}").findings()).isEmpty();
    assertThat(scanner.parse("").critical()).isZero();
  }
}
