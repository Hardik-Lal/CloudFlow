package com.cloudflow.deployment.engine;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Replaces secret values in log output. Very short values are not masked because they would corrupt
 * unrelated text and are not meaningful secrets anyway.
 */
public final class SecretMasker {

  static final String MASK = "********";
  private static final int MIN_SECRET_LENGTH = 4;

  private final List<String> secrets;

  public SecretMasker(Collection<String> secretValues) {
    // Longest first, so a secret containing another secret is masked as a whole.
    this.secrets =
        secretValues.stream()
            .filter(value -> value != null && value.length() >= MIN_SECRET_LENGTH)
            .distinct()
            .sorted(Comparator.comparingInt(String::length).reversed())
            .toList();
  }

  public static SecretMasker none() {
    return new SecretMasker(List.of());
  }

  public String mask(String text) {
    String masked = text;
    for (String secret : secrets) {
      masked = masked.replace(secret, MASK);
    }
    return masked;
  }
}
