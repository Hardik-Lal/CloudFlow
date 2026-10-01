package com.cloudflow.common.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * Authenticated encryption (AES-256-GCM) for secrets stored in the database.
 *
 * <p>Ciphertext format: {@code v1:<base64(iv || ciphertext || tag)>}. The version prefix allows key
 * rotation or algorithm changes without ambiguity for values already stored.
 */
@Service
public class EncryptionService {

  private static final String VERSION_PREFIX = "v1:";
  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int KEY_LENGTH_BYTES = 32;
  private static final int IV_LENGTH_BYTES = 12;
  private static final int TAG_LENGTH_BITS = 128;

  private final SecretKey key;
  private final SecureRandom secureRandom = new SecureRandom();

  public EncryptionService(EncryptionProperties properties) {
    byte[] keyBytes = decodeKey(properties.encryptionKey());
    this.key = new SecretKeySpec(keyBytes, "AES");
  }

  public String encrypt(String plaintext) {
    byte[] iv = new byte[IV_LENGTH_BYTES];
    secureRandom.nextBytes(iv);
    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
      byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      byte[] payload =
          ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array();
      return VERSION_PREFIX + Base64.getEncoder().encodeToString(payload);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Encryption failed", e);
    }
  }

  public String decrypt(String encrypted) {
    if (encrypted == null || !encrypted.startsWith(VERSION_PREFIX)) {
      throw new IllegalArgumentException("Unsupported ciphertext format");
    }
    byte[] payload = Base64.getDecoder().decode(encrypted.substring(VERSION_PREFIX.length()));
    if (payload.length <= IV_LENGTH_BYTES) {
      throw new IllegalArgumentException("Ciphertext is too short");
    }
    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(
          Cipher.DECRYPT_MODE,
          key,
          new GCMParameterSpec(TAG_LENGTH_BITS, payload, 0, IV_LENGTH_BYTES));
      byte[] plaintext = cipher.doFinal(payload, IV_LENGTH_BYTES, payload.length - IV_LENGTH_BYTES);
      return new String(plaintext, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(
          "Decryption failed: ciphertext was tampered with or key is wrong", e);
    }
  }

  private static byte[] decodeKey(String base64Key) {
    byte[] keyBytes;
    try {
      keyBytes = Base64.getDecoder().decode(base64Key);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("cloudflow.crypto.encryption-key must be valid base64", e);
    }
    if (keyBytes.length != KEY_LENGTH_BYTES) {
      throw new IllegalStateException(
          "cloudflow.crypto.encryption-key must decode to exactly 32 bytes (AES-256)");
    }
    return keyBytes;
  }
}
