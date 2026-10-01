package com.cloudflow.deployment.engine;

import com.cloudflow.deployment.config.DeploymentProperties;
import com.cloudflow.deployment.config.KubernetesProperties;
import com.cloudflow.environment.domain.DeploymentTarget;
import java.net.URI;
import org.springframework.stereotype.Component;

/**
 * Where a deployed workload can be reached. Docker: by container name over the application network
 * (backend running in Docker) or through the published host port (backend on the host). Kubernetes:
 * through the NodePort of the workload's service.
 */
@Component
public class DeploymentEndpoints {

  private final DeploymentProperties properties;
  private final KubernetesProperties kubernetes;

  public DeploymentEndpoints(DeploymentProperties properties, KubernetesProperties kubernetes) {
    this.properties = properties;
    this.kubernetes = kubernetes;
  }

  /**
   * @return the health probe URL, or {@code null} if the workload cannot be reached
   */
  public URI probeUrl(
      DeploymentTarget target,
      String containerName,
      Integer hostPort,
      int containerPort,
      String path) {
    if (target == DeploymentTarget.KUBERNETES) {
      return hostPort == null
          ? null
          : URI.create("http://" + kubernetes.nodeHost() + ":" + hostPort + path);
    }
    if (properties.probesViaPublishedPort()) {
      return hostPort == null
          ? null
          : URI.create("http://" + properties.healthCheckHost() + ":" + hostPort + path);
    }
    return containerName == null
        ? null
        : URI.create("http://" + containerName + ":" + containerPort + path);
  }

  /** Host name in the public URL of an application deployed to {@code target}. */
  public String publicHost(DeploymentTarget target) {
    return target == DeploymentTarget.KUBERNETES
        ? kubernetes.publicHost()
        : properties.publicHost();
  }
}
