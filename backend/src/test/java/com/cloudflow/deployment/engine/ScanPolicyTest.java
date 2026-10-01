package com.cloudflow.deployment.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.cloudflow.deployment.domain.ScanStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScanPolicyTest {

  @Test
  void blocksCriticalFindingsOnlyWhenConfigured() {
    ScanResult critical = new ScanResult(1, 0, List.of());
    ScanResult high = new ScanResult(0, 3, List.of());
    ScanResult clean = new ScanResult(0, 0, List.of());

    assertThat(ScanPolicy.evaluate(critical, true)).isEqualTo(ScanStatus.BLOCKED);
    assertThat(ScanPolicy.evaluate(critical, false)).isEqualTo(ScanStatus.VULNERABLE);
    assertThat(ScanPolicy.evaluate(high, true)).isEqualTo(ScanStatus.VULNERABLE);
    assertThat(ScanPolicy.evaluate(clean, true)).isEqualTo(ScanStatus.PASSED);
  }
}
