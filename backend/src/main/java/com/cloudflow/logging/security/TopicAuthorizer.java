package com.cloudflow.logging.security;

import com.cloudflow.deployment.service.DeploymentService;
import com.cloudflow.environment.service.EnvironmentAccessService;
import com.cloudflow.organization.domain.Permission;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/** Decides whether a user may subscribe to a STOMP topic. Unknown topics are always denied. */
@Component
public class TopicAuthorizer {

  static final Pattern DEPLOYMENT_TOPIC =
      Pattern.compile("^/topic/deployments/([0-9a-f-]{36})/(logs|status)$");
  static final Pattern ENVIRONMENT_TOPIC =
      Pattern.compile("^/topic/environments/([0-9a-f-]{36})/(logs|metrics)$");

  private final DeploymentService deploymentService;
  private final EnvironmentAccessService environmentAccess;

  public TopicAuthorizer(
      DeploymentService deploymentService, EnvironmentAccessService environmentAccess) {
    this.deploymentService = deploymentService;
    this.environmentAccess = environmentAccess;
  }

  /**
   * @throws AccessDeniedException if the topic is unknown or the user may not read it
   */
  public void authorize(String destination, UUID userId) {
    if (destination == null) {
      throw new AccessDeniedException("Missing destination");
    }
    Matcher deployment = DEPLOYMENT_TOPIC.matcher(destination);
    if (deployment.matches()) {
      guard(() -> deploymentService.authorizeRead(UUID.fromString(deployment.group(1)), userId));
      return;
    }
    Matcher environment = ENVIRONMENT_TOPIC.matcher(destination);
    if (environment.matches()) {
      guard(
          () ->
              environmentAccess.require(
                  UUID.fromString(environment.group(1)),
                  userId,
                  Permission.DEPLOYMENT_READ,
                  Permission.DEPLOYMENT_READ));
      return;
    }
    throw new AccessDeniedException("Unknown topic");
  }

  /** The environment id of an environment log topic, if the destination is one. */
  public static Optional<UUID> environmentLogTopic(String destination) {
    if (destination == null) {
      return Optional.empty();
    }
    Matcher matcher = ENVIRONMENT_TOPIC.matcher(destination);
    return matcher.matches() && "logs".equals(matcher.group(2))
        ? Optional.of(UUID.fromString(matcher.group(1)))
        : Optional.empty();
  }

  private static void guard(Runnable check) {
    try {
      check.run();
    } catch (RuntimeException e) {
      // Hide whether the resource exists: every failure is reported the same way.
      throw new AccessDeniedException("Not allowed to subscribe");
    }
  }
}
