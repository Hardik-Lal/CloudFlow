package com.cloudflow.deployment.engine;

import com.cloudflow.environment.service.VariableService;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Builds a {@link SecretMasker} for the secret variables of an environment. */
@Component
public class EnvironmentSecretMasker {

  private final VariableService variables;

  public EnvironmentSecretMasker(VariableService variables) {
    this.variables = variables;
  }

  public SecretMasker forEnvironment(UUID environmentId) {
    Map<String, String> values = variables.resolveForDeployment(environmentId);
    return new SecretMasker(variables.secretKeys(environmentId).stream().map(values::get).toList());
  }
}
