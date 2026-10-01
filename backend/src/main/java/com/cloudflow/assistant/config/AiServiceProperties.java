package com.cloudflow.assistant.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * @param baseUrl AI service URL on the internal platform network
 * @param internalToken shared secret sent as {@code X-Internal-Token}; AI features are disabled
 *     when empty
 * @param timeout read timeout (model calls can take tens of seconds)
 */
@Validated
@ConfigurationProperties("cloudflow.ai")
public record AiServiceProperties(
    @DefaultValue("http://localhost:8000") @NotBlank String baseUrl,
    @DefaultValue("") String internalToken,
    @DefaultValue("120s") @NotNull Duration timeout) {

  public boolean enabled() {
    return internalToken != null && !internalToken.isBlank();
  }
}
