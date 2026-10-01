package com.cloudflow.common.exception;

/** Thrown when a request conflicts with the current state (duplicates, invalid transitions). */
public class ConflictException extends RuntimeException {

  public ConflictException(String message) {
    super(message);
  }
}
