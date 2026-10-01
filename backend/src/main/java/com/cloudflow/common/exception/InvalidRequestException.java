package com.cloudflow.common.exception;

/** Thrown when a request is well-formed but violates a rule checked in the service layer. */
public class InvalidRequestException extends RuntimeException {

  private final String field;

  public InvalidRequestException(String field, String message) {
    super(message);
    this.field = field;
  }

  public String getField() {
    return field;
  }
}
