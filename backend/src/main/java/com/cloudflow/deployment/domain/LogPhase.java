package com.cloudflow.deployment.domain;

public enum LogPhase {
  SOURCE,
  BUILD,
  SCAN,
  PUSH,
  DEPLOY,
  HEALTH_CHECK,
  RUNTIME
}
