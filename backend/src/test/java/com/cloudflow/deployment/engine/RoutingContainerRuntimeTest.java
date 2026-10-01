package com.cloudflow.deployment.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudflow.deployment.engine.ContainerRuntime.RuntimeFailure;
import com.cloudflow.environment.domain.DeploymentTarget;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RoutingContainerRuntimeTest {

  private final DockerContainerRuntime docker = mock(DockerContainerRuntime.class);
  private final KubernetesWorkloadRuntime kubernetes = mock(KubernetesWorkloadRuntime.class);

  RoutingContainerRuntimeTest() {
    when(docker.target()).thenReturn(DeploymentTarget.DOCKER);
    when(docker.owns(any())).thenAnswer(call -> !call.<String>getArgument(0).contains(":"));
    when(kubernetes.target()).thenReturn(DeploymentTarget.KUBERNETES);
    when(kubernetes.owns(any())).thenAnswer(call -> call.<String>getArgument(0).startsWith("k8s:"));
  }

  @Test
  void runsOnTheEnvironmentsTargetAndRoutesLaterCallsByWorkloadId() {
    RoutingContainerRuntime router =
        new RoutingContainerRuntime(docker, List.of(docker, kubernetes));
    ContainerSpec spec = spec(DeploymentTarget.KUBERNETES);
    when(kubernetes.run(spec)).thenReturn(new RunningContainer("k8s:apps/web", "web", 30001));

    assertThat(router.run(spec).id()).isEqualTo("k8s:apps/web");
    router.remove("k8s:apps/web");
    router.remove("3f2a9c");

    verify(kubernetes).remove("k8s:apps/web");
    verify(docker).remove("3f2a9c");
    verify(docker, never()).run(any());
  }

  @Test
  void imagesAreAlwaysBuiltWithDocker() {
    RoutingContainerRuntime router =
        new RoutingContainerRuntime(docker, List.of(docker, kubernetes));

    router.imageExists("localhost:5000/acme/web:dev-1");

    verify(docker).imageExists("localhost:5000/acme/web:dev-1");
  }

  @Test
  void anUnconfiguredTargetIsAFailureNotACrash() {
    RoutingContainerRuntime dockerOnly = new RoutingContainerRuntime(docker, List.of(docker));

    assertThatThrownBy(() -> dockerOnly.run(spec(DeploymentTarget.KUBERNETES)))
        .isInstanceOf(RuntimeFailure.class)
        .hasMessageContaining("Kubernetes is not configured");
    assertThatThrownBy(() -> dockerOnly.inspect("k8s:apps/web")).isInstanceOf(RuntimeFailure.class);
  }

  private static ContainerSpec spec(DeploymentTarget target) {
    return new ContainerSpec(
        target, "web", "img", Map.of(), 8080, "net", "vol", "/data", null, null, Map.of());
  }
}
