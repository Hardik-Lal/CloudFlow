package com.cloudflow.deployment.engine;

import java.util.Optional;
import java.util.function.Consumer;

/** Scans container images for known vulnerabilities. */
public interface ImageScanner {

  /**
   * @param output receives progress and error messages
   * @return the result, or empty when scanning is disabled or the scanner could not run
   */
  Optional<ScanResult> scan(String imageTag, Consumer<String> output);
}
