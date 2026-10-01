package com.cloudflow.common.crypto;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param encryptionKey base64-encoded 256-bit AES key used to encrypt secrets at rest
 */
@Validated
@ConfigurationProperties("cloudflow.crypto")
public record EncryptionProperties(@NotBlank String encryptionKey) {}
