package com.cloudflow.common.exception;

import java.util.List;

/**
 * Thrown when a well-formed request cannot be carried out because the target is in an invalid
 * state, e.g. deploying an environment whose configuration fails validation.
 */
public class UnprocessableException extends RuntimeException {

  private final List<FieldIssue> errors;

  public UnprocessableException(String message, List<FieldIssue> errors) {
    super(message);
    this.errors = List.copyOf(errors);
  }

  public List<FieldIssue> getErrors() {
    return errors;
  }

  public record FieldIssue(String field, String message) {}
}
