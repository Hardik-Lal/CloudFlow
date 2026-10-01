package com.cloudflow.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class EncryptionServiceTest {

  private static final String KEY =
      Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());

  private final EncryptionService service = new EncryptionService(new EncryptionProperties(KEY));

  @Test
  void roundTripsPlaintext() {
    String encrypted = service.encrypt("s3cr3t-value ✓");

    assertThat(encrypted).startsWith("v1:").doesNotContain("s3cr3t");
    assertThat(service.decrypt(encrypted)).isEqualTo("s3cr3t-value ✓");
  }

  @Test
  void usesRandomIvSoEqualPlaintextsProduceDifferentCiphertexts() {
    assertThat(service.encrypt("same")).isNotEqualTo(service.encrypt("same"));
  }

  @Test
  void rejectsTamperedCiphertext() {
    byte[] payload = Base64.getDecoder().decode(service.encrypt("value").substring(3));
    payload[payload.length - 1] ^= 1;
    String tampered = "v1:" + Base64.getEncoder().encodeToString(payload);

    assertThatThrownBy(() -> service.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectsCiphertextFromAnotherKey() {
    String otherKey =
        Base64.getEncoder().encodeToString("ffffffffffffffffffffffffffffffff".getBytes());
    String encrypted = new EncryptionService(new EncryptionProperties(otherKey)).encrypt("value");

    assertThatThrownBy(() -> service.decrypt(encrypted)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectsKeysThatAreNotAes256() {
    String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

    assertThatThrownBy(() -> new EncryptionService(new EncryptionProperties(shortKey)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("32 bytes");
  }
}
