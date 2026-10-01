package com.cloudflow.common.exception;

/** Thrown when an upstream dependency (GitHub, Docker, AI service) fails or is unreachable. */
public class ExternalServiceException extends RuntimeException {

  private final String service;

  public ExternalServiceException(String service, String message, Throwable cause) {
    super(message, cause);
    this.service = service;
  }

  public ExternalServiceException(String service, String message) {
    this(service, message, null);
  }

  public String getService() {
    return service;
  }
}
