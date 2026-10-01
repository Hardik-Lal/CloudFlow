package com.cloudflow.environment.domain;

public enum EnvironmentType {
  DEVELOPMENT,
  STAGING,
  PRODUCTION;

  public boolean isProduction() {
    return this == PRODUCTION;
  }
}
