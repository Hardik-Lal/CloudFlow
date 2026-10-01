package com.cloudflow.logging.service;

import com.cloudflow.deployment.domain.DeploymentStatus;
import com.cloudflow.deployment.dto.ActiveDeployment;
import com.cloudflow.deployment.engine.ContainerRuntime;
import com.cloudflow.deployment.engine.EnvironmentSecretMasker;
import com.cloudflow.deployment.engine.SecretMasker;
import com.cloudflow.deployment.service.DeploymentQueryService;
import com.cloudflow.deployment.service.DeploymentStatusChangedEvent;
import com.cloudflow.logging.dto.RuntimeLogLine;
import com.cloudflow.logging.security.TopicAuthorizer;
import jakarta.annotation.PreDestroy;
import java.io.Closeable;
import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

/**
 * Streams the output of an environment's running container to {@code
 * /topic/environments/{id}/logs}. A container is followed only while at least one browser is
 * subscribed, and the stream switches to the new container when a deployment goes live.
 */
@Component
public class RuntimeLogStreamer {

  private static final Logger log = LoggerFactory.getLogger(RuntimeLogStreamer.class);

  private final ContainerRuntime runtime;
  private final DeploymentQueryService deployments;
  private final EnvironmentSecretMasker secretMasker;
  private final SimpMessagingTemplate messaging;
  private final Clock clock;

  /** Guarded by {@code this}: subscription key (session + subscription id) to environment. */
  private final Map<String, UUID> subscriptions = new HashMap<>();

  /** Guarded by {@code this}: live followers per environment. */
  private final Map<UUID, Closeable> followers = new HashMap<>();

  public RuntimeLogStreamer(
      ContainerRuntime runtime,
      DeploymentQueryService deployments,
      EnvironmentSecretMasker secretMasker,
      SimpMessagingTemplate messaging,
      Clock clock) {
    this.runtime = runtime;
    this.deployments = deployments;
    this.secretMasker = secretMasker;
    this.messaging = messaging;
    this.clock = clock;
  }

  @EventListener
  public synchronized void onSubscribe(SessionSubscribeEvent event) {
    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
    Optional<UUID> environmentId = TopicAuthorizer.environmentLogTopic(accessor.getDestination());
    if (environmentId.isEmpty()) {
      return;
    }
    subscriptions.put(
        key(accessor.getSessionId(), accessor.getSubscriptionId()), environmentId.get());
    if (!followers.containsKey(environmentId.get())) {
      follow(environmentId.get());
    }
  }

  @EventListener
  public synchronized void onUnsubscribe(SessionUnsubscribeEvent event) {
    StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
    UUID environmentId =
        subscriptions.remove(key(accessor.getSessionId(), accessor.getSubscriptionId()));
    if (environmentId != null) {
      stopIfUnwatched(environmentId);
    }
  }

  @EventListener
  public synchronized void onDisconnect(SessionDisconnectEvent event) {
    String prefix = event.getSessionId() + ":";
    subscriptions.entrySet().stream()
        .filter(entry -> entry.getKey().startsWith(prefix))
        .map(Map.Entry::getValue)
        .toList()
        .forEach(
            environmentId -> {
              subscriptions.values().remove(environmentId);
              stopIfUnwatched(environmentId);
            });
    subscriptions.keySet().removeIf(key -> key.startsWith(prefix));
  }

  /** A new deployment went live: follow its container instead of the retired one. */
  @TransactionalEventListener(fallbackExecution = true)
  public synchronized void onDeploymentStatus(DeploymentStatusChangedEvent event) {
    if (event.status() == DeploymentStatus.SUCCEEDED
        && followers.containsKey(event.environmentId())) {
      close(followers.remove(event.environmentId()));
      follow(event.environmentId());
    }
  }

  @PreDestroy
  public synchronized void shutdown() {
    followers.values().forEach(RuntimeLogStreamer::close);
    followers.clear();
  }

  private void follow(UUID environmentId) {
    String topic = "/topic/environments/" + environmentId + "/logs";
    Optional<ActiveDeployment> active = deployments.activeFor(environmentId);
    if (active.isEmpty() || active.get().containerId() == null) {
      return;
    }
    SecretMasker masker = secretMasker.forEnvironment(environmentId);
    UUID deploymentId = active.get().deploymentId();
    try {
      // Tail 0: clients load recent history over REST before subscribing.
      Closeable follower =
          runtime.followLogs(
              active.get().containerId(),
              0,
              line ->
                  messaging.convertAndSend(
                      topic, new RuntimeLogLine(deploymentId, masker.mask(line), clock.instant())));
      followers.put(environmentId, follower);
    } catch (RuntimeException e) {
      log.warn("Could not follow logs of environment {}", environmentId, e);
    }
  }

  private void stopIfUnwatched(UUID environmentId) {
    if (!subscriptions.containsValue(environmentId)) {
      close(followers.remove(environmentId));
    }
  }

  private static String key(String sessionId, String subscriptionId) {
    return sessionId + ":" + subscriptionId;
  }

  private static void close(Closeable follower) {
    if (follower == null) {
      return;
    }
    try {
      follower.close();
    } catch (IOException e) {
      log.debug("Error closing log follower", e);
    }
  }
}
