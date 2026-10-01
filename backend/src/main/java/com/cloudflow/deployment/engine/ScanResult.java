package com.cloudflow.deployment.engine;

import java.util.List;

/** HIGH and CRITICAL vulnerabilities found in an image. */
public record ScanResult(int critical, int high, List<Finding> findings) {

  public record Finding(
      String id,
      String severity,
      String packageName,
      String installedVersion,
      String fixedVersion,
      String title) {}
}
