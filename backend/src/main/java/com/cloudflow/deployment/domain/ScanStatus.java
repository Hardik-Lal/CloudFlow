package com.cloudflow.deployment.domain;

/** Outcome of the image vulnerability scan of a deployment. */
public enum ScanStatus {
  /** No HIGH or CRITICAL vulnerabilities. */
  PASSED,
  /** HIGH or CRITICAL vulnerabilities found; deployed because the policy allows it. */
  VULNERABLE,
  /** CRITICAL vulnerabilities found and the policy blocks deployment. */
  BLOCKED,
  /** The scanner could not run; the deployment continued. */
  ERROR,
  /** Scanning is disabled, or the deployment reused an already scanned image (rollback). */
  SKIPPED
}
