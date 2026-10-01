package com.cloudflow.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SlugsTest {

  @ParameterizedTest
  @CsvSource({
    "Acme Platform, acme-platform",
    "'  Hello,   World!  ', hello-world",
    "Café Déjà Vu, cafe-deja-vu",
    "---Already-Slugged---, already-slugged",
    "Team_42 / Core, team-42-core"
  })
  void slugifiesFreeText(String input, String expected) {
    assertThat(Slugs.slugify(input, 50)).isEqualTo(expected).matches(Slugs.PATTERN);
  }

  @Test
  void truncatesWithoutLeavingTrailingHyphen() {
    assertThat(Slugs.slugify("abc def ghi", 4)).isEqualTo("abc");
  }

  @Test
  void returnsEmptyWhenNothingUsable() {
    assertThat(Slugs.slugify("!!!", 50)).isEmpty();
  }
}
