package com.cloudflow.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.cloudflow.assistant.domain.SuggestionType;
import org.junit.jupiter.api.Test;

class SuggestionPathsTest {

  @Test
  void acceptsSafePathsForTheType() {
    assertThat(SuggestionPaths.resolve(SuggestionType.DOCKERFILE, "api/Dockerfile"))
        .isEqualTo("api/Dockerfile");
    assertThat(SuggestionPaths.resolve(SuggestionType.WORKFLOW, ".github/workflows/test.yml"))
        .isEqualTo(".github/workflows/test.yml");
    assertThat(SuggestionPaths.resolve(SuggestionType.DOCUMENTATION, "docs/SETUP.md"))
        .isEqualTo("docs/SETUP.md");
  }

  @Test
  void fallsBackForUnsafeOrForeignPaths() {
    assertThat(SuggestionPaths.resolve(SuggestionType.DOCKERFILE, "../../etc/passwd"))
        .isEqualTo("Dockerfile");
    assertThat(SuggestionPaths.resolve(SuggestionType.DOCKERFILE, "src/main/App.java"))
        .isEqualTo("Dockerfile");
    assertThat(
            SuggestionPaths.resolve(
                SuggestionType.WORKFLOW, ".github/workflows/cloudflow-production.yml"))
        .isEqualTo(".github/workflows/ci.yml");
    assertThat(SuggestionPaths.resolve(SuggestionType.DOCUMENTATION, "/abs/README.md"))
        .isEqualTo("docs/CLOUDFLOW.md");
    assertThat(SuggestionPaths.resolve(SuggestionType.ENV_TEMPLATE, "anything"))
        .isEqualTo("variables");
  }
}
