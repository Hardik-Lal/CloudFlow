package com.cloudflow.environment.dto;

import java.util.List;

/** Outcome of validating a deployment configuration. Errors block deployment; warnings do not. */
public record ValidationResult(
    boolean valid, List<ValidationIssue> errors, List<ValidationIssue> warnings) {

  public static ValidationResult of(List<ValidationIssue> errors, List<ValidationIssue> warnings) {
    return new ValidationResult(errors.isEmpty(), List.copyOf(errors), List.copyOf(warnings));
  }
}
