package com.cloudflow.common.util;

import com.cloudflow.common.exception.InvalidRequestException;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** URL-safe identifier helpers. */
public final class Slugs {

  /** Lowercase alphanumerics separated by single hyphens. */
  public static final String PATTERN = "^[a-z0-9]+(-[a-z0-9]+)*$";

  public static final int MIN_LENGTH = 3;
  public static final int MAX_LENGTH = 50;

  private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
  private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
  private static final Pattern EDGE_HYPHENS = Pattern.compile("(^-+)|(-+$)");

  private Slugs() {}

  /**
   * Returns the explicitly requested slug (already validated by the request DTO), or derives one
   * from {@code name}.
   *
   * @throws InvalidRequestException if no slug of at least {@link #MIN_LENGTH} characters results
   */
  public static String requestedOrDerived(String requestedSlug, String name) {
    String slug = requestedSlug != null ? requestedSlug : slugify(name, MAX_LENGTH);
    if (slug.length() < MIN_LENGTH) {
      throw new InvalidRequestException(
          "slug", "Could not derive a slug of at least 3 characters from the name; provide one");
    }
    return slug;
  }

  /**
   * Derives a slug from free text, e.g. {@code "Acme Platform!"} becomes {@code "acme-platform"}.
   */
  public static String slugify(String text, int maxLength) {
    String normalized =
        DIACRITICS
            .matcher(Normalizer.normalize(text, Normalizer.Form.NFD))
            .replaceAll("")
            .toLowerCase(Locale.ROOT);
    String slug = NON_ALPHANUMERIC.matcher(normalized).replaceAll("-");
    slug = EDGE_HYPHENS.matcher(slug).replaceAll("");
    if (slug.length() > maxLength) {
      slug = EDGE_HYPHENS.matcher(slug.substring(0, maxLength)).replaceAll("");
    }
    return slug;
  }
}
