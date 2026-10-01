package com.cloudflow.common.exception;

/**
 * Thrown when a resource does not exist or is not visible to the caller. Both cases map to 404 so
 * that resources in other organizations are never revealed.
 */
public class ResourceNotFoundException extends RuntimeException {

  public ResourceNotFoundException(String resourceName, Object identifier) {
    super(resourceName + " '" + identifier + "' was not found");
  }
}
