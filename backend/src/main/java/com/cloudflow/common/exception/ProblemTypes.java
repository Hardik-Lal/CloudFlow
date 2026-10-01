package com.cloudflow.common.exception;

import java.net.URI;

/** Stable problem {@code type} identifiers returned in RFC 9457 error responses. */
public final class ProblemTypes {

  public static final URI VALIDATION = of("validation-error");
  public static final URI NOT_FOUND = of("not-found");
  public static final URI CONFLICT = of("conflict");
  public static final URI UNPROCESSABLE = of("unprocessable");
  public static final URI UNAUTHORIZED = of("unauthorized");
  public static final URI FORBIDDEN = of("forbidden");
  public static final URI UPSTREAM = of("upstream-error");
  public static final URI RATE_LIMITED = of("rate-limited");
  public static final URI INTERNAL = of("internal-error");

  private ProblemTypes() {}

  private static URI of(String slug) {
    return URI.create("urn:cloudflow:problem:" + slug);
  }
}
