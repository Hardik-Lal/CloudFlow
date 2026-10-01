package com.cloudflow.common.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Web-facing settings shared by all modules.
 *
 * @param frontendUrl base URL of the CloudFlow frontend, used for post-login redirects
 * @param corsAllowedOrigins origins allowed to call the API from a browser
 */
@Validated
@ConfigurationProperties("cloudflow.web")
public record WebProperties(
    @NotBlank String frontendUrl, @NotEmpty List<String> corsAllowedOrigins) {}
