package com.cloudflow.common.exception;

/** Thrown when a request lacks valid credentials outside the bearer-token filter chain. */
public class UnauthorizedException extends RuntimeException {

  public UnauthorizedException(String message) {
    super(message);
  }
}
