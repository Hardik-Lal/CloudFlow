package com.cloudflow.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.cloudflow.assistant.service.EnvTemplateParser.TemplateVariable;
import org.junit.jupiter.api.Test;

class EnvTemplateParserTest {

  @Test
  void parsesVariablesSecretsAndQuotes() {
    String template =
        """
        # Application settings
        PORT=8000
        export LOG_LEVEL="info"
        DATABASE_URL=postgres://user:password@db:5432/app  # secret
        api_key='abc' # SECRET

        not a variable
        CLOUDFLOW_TOKEN=reserved
        """;

    assertThat(EnvTemplateParser.parse(template))
        .containsExactly(
            new TemplateVariable("PORT", "8000", false),
            new TemplateVariable("LOG_LEVEL", "info", false),
            new TemplateVariable("DATABASE_URL", "postgres://user:password@db:5432/app", true),
            new TemplateVariable("API_KEY", "abc", true));
  }
}
