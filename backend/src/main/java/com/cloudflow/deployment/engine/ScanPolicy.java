package com.cloudflow.deployment.engine;

import com.cloudflow.deployment.domain.ScanStatus;

/** Turns scan findings into a deployment decision. */
public final class ScanPolicy {

  private ScanPolicy() {}

  public static ScanStatus evaluate(ScanResult result, boolean blockOnCritical) {
    if (result.critical() > 0 && blockOnCritical) {
      return ScanStatus.BLOCKED;
    }
    return result.critical() + result.high() > 0 ? ScanStatus.VULNERABLE : ScanStatus.PASSED;
  }
}
