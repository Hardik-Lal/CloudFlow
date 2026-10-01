package com.cloudflow.environment.service;

import com.cloudflow.audit.domain.AuditAction;
import com.cloudflow.audit.service.AuditLogger;
import com.cloudflow.common.crypto.EncryptionService;
import com.cloudflow.common.exception.InvalidRequestException;
import com.cloudflow.common.exception.ResourceNotFoundException;
import com.cloudflow.environment.domain.EnvironmentVariable;
import com.cloudflow.environment.dto.EnvironmentRef;
import com.cloudflow.environment.dto.PutVariableRequest;
import com.cloudflow.environment.dto.VariableResponse;
import com.cloudflow.environment.repository.EnvironmentVariableRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Environment variables. Secret values are encrypted before they reach the database and are never
 * returned by the API; they are decrypted only for injection into deployed containers.
 */
@Service
public class VariableService {

  private final AuditLogger audit;

  private static final Pattern KEY = Pattern.compile(EnvironmentVariable.KEY_PATTERN);
  private static final int MAX_KEY_LENGTH = 128;

  private final EnvironmentVariableRepository variableRepository;
  private final EnvironmentAccessService accessService;
  private final EncryptionService encryptionService;

  public VariableService(
      EnvironmentVariableRepository variableRepository,
      EnvironmentAccessService accessService,
      EncryptionService encryptionService,
      AuditLogger audit) {
    this.audit = audit;
    this.variableRepository = variableRepository;
    this.accessService = accessService;
    this.encryptionService = encryptionService;
  }

  @Transactional(readOnly = true)
  public List<VariableResponse> list(UUID environmentId, UUID userId) {
    accessService.requireRead(environmentId, userId);
    return variableRepository.findAllByEnvironmentIdOrderByKeyAsc(environmentId).stream()
        .map(VariableResponse::from)
        .toList();
  }

  /** Creates or replaces a variable. */
  @Transactional
  public PutResult put(UUID environmentId, UUID userId, String key, PutVariableRequest request) {
    EnvironmentRef environment = accessService.requireWrite(environmentId, userId);
    validateKey(key);
    String storedValue =
        request.secret() ? encryptionService.encrypt(request.value()) : request.value();

    // The value itself is never audited, only which key changed and whether it is secret.
    audit.record(
        environment.organizationId(),
        userId,
        AuditAction.VARIABLE_SET,
        "variable",
        key,
        Map.of(
            "environmentId", environmentId.toString(),
            "environment", environment.type().name(),
            "secret", String.valueOf(request.secret())));
    return variableRepository
        .findByEnvironmentIdAndKey(environmentId, key)
        .map(
            existing -> {
              existing.replace(storedValue, request.secret(), userId);
              return new PutResult(VariableResponse.from(existing), false);
            })
        .orElseGet(
            () ->
                new PutResult(
                    VariableResponse.from(
                        variableRepository.save(
                            new EnvironmentVariable(
                                environmentId, key, storedValue, request.secret(), userId))),
                    true));
  }

  @Transactional
  public void delete(UUID environmentId, UUID userId, String key) {
    EnvironmentRef environment = accessService.requireWrite(environmentId, userId);
    EnvironmentVariable variable =
        variableRepository
            .findByEnvironmentIdAndKey(environmentId, key)
            .orElseThrow(() -> new ResourceNotFoundException("Variable", key));
    variableRepository.delete(variable);
    audit.record(
        environment.organizationId(),
        userId,
        AuditAction.VARIABLE_DELETED,
        "variable",
        key,
        Map.of(
            "environmentId", environmentId.toString(), "environment", environment.type().name()));
  }

  /**
   * Decrypted variables for a deployment. Internal use only: the caller must have authorized the
   * deployment. Never expose the result through the API.
   */
  @Transactional(readOnly = true)
  public Map<String, String> resolveForDeployment(UUID environmentId) {
    Map<String, String> resolved = new LinkedHashMap<>();
    for (EnvironmentVariable variable :
        variableRepository.findAllByEnvironmentIdOrderByKeyAsc(environmentId)) {
      resolved.put(
          variable.getKey(),
          variable.isSecret()
              ? encryptionService.decrypt(variable.getStoredValue())
              : variable.getStoredValue());
    }
    return resolved;
  }

  /** Keys of the variables that are secrets, so their values can be masked in logs. */
  @Transactional(readOnly = true)
  public List<String> secretKeys(UUID environmentId) {
    return variableRepository.findAllByEnvironmentIdOrderByKeyAsc(environmentId).stream()
        .filter(EnvironmentVariable::isSecret)
        .map(EnvironmentVariable::getKey)
        .toList();
  }

  private static void validateKey(String key) {
    if (key.length() > MAX_KEY_LENGTH || !KEY.matcher(key).matches()) {
      throw new InvalidRequestException(
          "key", "Must use upper-case letters, digits, and underscores, not starting with a digit");
    }
    if (key.startsWith(EnvironmentVariable.RESERVED_PREFIX)) {
      throw new InvalidRequestException(
          "key", "The CLOUDFLOW_ prefix is reserved for variables set by CloudFlow");
    }
  }

  public record PutResult(VariableResponse variable, boolean created) {}
}
