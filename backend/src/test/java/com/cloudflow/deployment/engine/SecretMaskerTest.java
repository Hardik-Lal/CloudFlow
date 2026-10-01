package com.cloudflow.deployment.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SecretMaskerTest {

  @Test
  void masksEverySecretOccurrenceLongestFirst() {
    SecretMasker masker = new SecretMasker(List.of("hunter2", "hunter2-extended", "abc"));

    assertThat(masker.mask("password=hunter2-extended other=hunter2 short=abc"))
        .isEqualTo("password=******** other=******** short=abc");
  }

  @Test
  void noneLeavesTextUnchanged() {
    assertThat(SecretMasker.none().mask("nothing secret")).isEqualTo("nothing secret");
  }
}
