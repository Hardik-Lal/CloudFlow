package com.cloudflow.logging;

import static com.cloudflow.support.TestApi.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cloudflow.TestcontainersConfiguration;
import com.cloudflow.deployment.domain.Deployment;
import com.cloudflow.deployment.domain.LogLevel;
import com.cloudflow.deployment.domain.LogPhase;
import com.cloudflow.deployment.domain.TriggerType;
import com.cloudflow.deployment.dto.DeploymentLogResponse;
import com.cloudflow.deployment.engine.ContainerRuntime;
import com.cloudflow.deployment.engine.DeploymentLogAppendedEvent;
import com.cloudflow.deployment.engine.HealthChecker;
import com.cloudflow.deployment.engine.ImageScanner;
import com.cloudflow.deployment.repository.DeploymentRepository;
import com.cloudflow.github.client.GithubClient;
import com.cloudflow.support.TestApi;
import com.cloudflow.support.TestProjects;
import com.cloudflow.support.TestUsers;
import com.cloudflow.support.TestUsers.TestUser;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/** Real server + real STOMP client: authentication, authorization, and live log delivery. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestUsers.class, TestApi.class, TestProjects.class})
@MockitoBean(
    types = {GithubClient.class, ContainerRuntime.class, HealthChecker.class, ImageScanner.class})
class WebSocketIntegrationTest {

  @LocalServerPort private int port;
  @Autowired private TestApi api;
  @Autowired private TestUsers users;
  @Autowired private TestProjects projects;
  @Autowired private DeploymentRepository deployments;
  @Autowired private ApplicationEventPublisher events;

  private WebSocketStompClient stompClient;
  private TestUser owner;
  private UUID deploymentId;

  @BeforeEach
  void setUp() {
    stompClient = new WebSocketStompClient(new StandardWebSocketClient());
    stompClient.setMessageConverter(new JacksonJsonMessageConverter());

    owner = users.create("owner");
    String orgId = api.createOrganization(owner);
    String projectId = projects.create(owner, orgId, List.of("app.py"));
    String envId =
        read(
            api.post(
                owner,
                "/api/v1/projects/" + projectId + "/environments",
                "{\"type\":\"DEVELOPMENT\"}"),
            "$.id");
    deploymentId =
        deployments
            .save(
                Deployment.build(
                    UUID.fromString(projectId),
                    UUID.fromString(envId),
                    owner.id(),
                    TriggerType.MANUAL,
                    "main",
                    null))
            .getId();
  }

  @AfterEach
  void tearDown() {
    stompClient.stop();
  }

  @Test
  void authorizedSubscribersReceiveDeploymentLogLines() throws Exception {
    StompSession session = connect(owner.authorization());
    BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();
    session.subscribe(
        "/topic/deployments/" + deploymentId + "/logs",
        new StompSessionHandlerAdapter() {
          @Override
          public Type getPayloadType(StompHeaders headers) {
            return Map.class;
          }

          @Override
          @SuppressWarnings("unchecked")
          public void handleFrame(StompHeaders headers, Object payload) {
            received.add((Map<String, Object>) payload);
          }
        });
    // A RECEIPT would be cleaner, but the simple broker does not send one; give the SUBSCRIBE
    // frame time to be processed before publishing.
    Thread.sleep(300);

    events.publishEvent(
        new DeploymentLogAppendedEvent(
            deploymentId,
            new DeploymentLogResponse(
                1, LogPhase.BUILD, LogLevel.INFO, "Step 1/5 : FROM python", Instant.now())));

    Map<String, Object> line = received.poll(5, TimeUnit.SECONDS);
    assertThat(line).isNotNull().containsEntry("message", "Step 1/5 : FROM python");
    assertThat(line).containsEntry("phase", "BUILD");
  }

  @Test
  void subscribingToAnotherOrganizationsDeploymentClosesTheSession() throws Exception {
    TestUser outsider = users.create("outsider");
    CompletableFuture<StompHeaders> error = new CompletableFuture<>();
    StompSession session =
        connect(
            outsider.authorization(),
            new StompSessionHandlerAdapter() {
              @Override
              public void handleFrame(StompHeaders headers, Object payload) {
                error.complete(headers);
              }
            });

    session.subscribe(
        "/topic/deployments/" + deploymentId + "/logs", new StompSessionHandlerAdapter() {});

    assertThat(error.get(5, TimeUnit.SECONDS).getFirst("message")).contains("Failed to send");
    Thread.sleep(200);
    assertThat(session.isConnected()).isFalse();
  }

  @Test
  void connectingWithoutAValidTokenFails() {
    assertThatThrownBy(() -> connect("Bearer not-a-jwt")).isInstanceOf(ExecutionException.class);
    assertThatThrownBy(() -> connect(null)).isInstanceOf(ExecutionException.class);
  }

  private StompSession connect(String authorization) throws Exception {
    return connect(authorization, new StompSessionHandlerAdapter() {});
  }

  private StompSession connect(String authorization, StompSessionHandlerAdapter handler)
      throws Exception {
    StompHeaders connectHeaders = new StompHeaders();
    if (authorization != null) {
      connectHeaders.add("Authorization", authorization);
    }
    return stompClient
        .connectAsync(
            "ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), connectHeaders, handler)
        .get(5, TimeUnit.SECONDS);
  }
}
