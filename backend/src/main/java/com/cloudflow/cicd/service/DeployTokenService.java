package com.cloudflow.cicd.service;

import com.cloudflow.audit.domain.AuditAction;
import com.cloudflow.audit.service.AuditLogger;
import com.cloudflow.cicd.domain.DeployToken;
import com.cloudflow.cicd.dto.CreatedDeployTokenResponse;
import com.cloudflow.cicd.dto.DeployTokenResponse;
import com.cloudflow.cicd.repository.DeployTokenRepository;
import com.cloudflow.common.crypto.Hashing;
import com.cloudflow.common.exception.ResourceNotFoundException;
import com.cloudflow.common.exception.UnauthorizedException;
import com.cloudflow.environment.dto.EnvironmentRef;
import com.cloudflow.environment.service.EnvironmentAccessService;
import com.cloudflow.organization.domain.Permission;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Deploy tokens authenticate CI pipelines that deploy an environment. */
@Service
public class DeployTokenService {

  private final AuditLogger audit;

  static final String TOKEN_PREFIX = "cfd_";
  private static final char[] ALPHABET =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
  private static final int RANDOM_LENGTH = 40;
  private static final int DISPLAY_PREFIX_LENGTH = 12;

  private final DeployTokenRepository repository;
  private final EnvironmentAccessService environmentAccess;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  public DeployTokenService(
      DeployTokenRepository repository,
      EnvironmentAccessService environmentAccess,
      Clock clock,
      AuditLogger audit) {
    this.audit = audit;
    this.repository = repository;
    this.environmentAccess = environmentAccess;
    this.clock = clock;
  }

  /** Creates a token; the caller must be allowed to deploy the environment. */
  @Transactional
  public CreatedDeployTokenResponse create(UUID environmentId, UUID userId, String name) {
    EnvironmentRef environment = requireDeployPermission(environmentId, userId);
    String rawToken = generate();
    DeployToken token =
        repository.save(
            new DeployToken(
                environmentId,
                name.strip(),
                Hashing.sha256Hex(rawToken),
                rawToken.substring(0, DISPLAY_PREFIX_LENGTH),
                userId));
    audit.record(
        environment.organizationId(),
        userId,
        AuditAction.DEPLOY_TOKEN_CREATED,
        "deploy-token",
        token.getId(),
        Map.of(
            "name", token.getName(),
            "prefix", token.getTokenPrefix(),
            "environment", environment.type().name()));
    return new CreatedDeployTokenResponse(
        DeployTokenResponse.from(token),
        rawToken,
        WorkflowGenerator.deployTokenSecretName(environment.type()));
  }

  @Transactional(readOnly = true)
  public List<DeployTokenResponse> list(UUID environmentId, UUID userId) {
    environmentAccess.require(
        environmentId, userId, Permission.PIPELINE_READ, Permission.PIPELINE_READ);
    return repository.findAllByEnvironmentIdOrderByCreatedAtDesc(environmentId).stream()
        .map(DeployTokenResponse::from)
        .toList();
  }

  @Transactional
  public void revoke(UUID tokenId, UUID userId) {
    DeployToken token =
        repository
            .findById(tokenId)
            .orElseThrow(() -> new ResourceNotFoundException("Deploy token", tokenId));
    EnvironmentRef environment;
    try {
      environment = requireDeployPermission(token.getEnvironmentId(), userId);
    } catch (ResourceNotFoundException e) {
      throw new ResourceNotFoundException("Deploy token", tokenId);
    }
    token.revoke(clock.instant());
    audit.record(
        environment.organizationId(),
        userId,
        AuditAction.DEPLOY_TOKEN_REVOKED,
        "deploy-token",
        tokenId,
        Map.of("name", token.getName(), "prefix", token.getTokenPrefix()));
  }

  /**
   * Resolves a raw token presented by a pipeline.
   *
   * @throws UnauthorizedException if the token is unknown or revoked
   */
  @Transactional
  public DeployTokenPrincipal authenticate(String rawToken) {
    if (rawToken == null || !rawToken.startsWith(TOKEN_PREFIX)) {
      throw new UnauthorizedException("Missing or malformed deploy token");
    }
    DeployToken token =
        repository
            .findByTokenHash(Hashing.sha256Hex(rawToken))
            .filter(candidate -> !candidate.isRevoked())
            .orElseThrow(() -> new UnauthorizedException("Invalid deploy token"));
    token.markUsed(clock.instant());
    return new DeployTokenPrincipal(token.getId(), token.getEnvironmentId(), token.getCreatedBy());
  }

  private EnvironmentRef requireDeployPermission(UUID environmentId, UUID userId) {
    return environmentAccess.require(
        environmentId,
        userId,
        Permission.DEPLOYMENT_TRIGGER,
        Permission.DEPLOYMENT_TRIGGER_PRODUCTION);
  }

  private String generate() {
    StringBuilder token = new StringBuilder(TOKEN_PREFIX);
    for (int i = 0; i < RANDOM_LENGTH; i++) {
      token.append(ALPHABET[random.nextInt(ALPHABET.length)]);
    }
    return token.toString();
  }

  /**
   * @param actingUserId the token's creator, whose permissions and GitHub access are used
   */
  public record DeployTokenPrincipal(UUID tokenId, UUID environmentId, UUID actingUserId) {}
}
