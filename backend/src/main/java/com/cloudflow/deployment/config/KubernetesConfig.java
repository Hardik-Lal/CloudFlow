package com.cloudflow.deployment.config;

import com.cloudflow.deployment.engine.KubernetesWorkloadRuntime;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/** The Kubernetes deployment target; only created when {@code cloudflow.kubernetes.enabled}. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "cloudflow.kubernetes.enabled", havingValue = "true")
public class KubernetesConfig {

  @Bean(destroyMethod = "close")
  KubernetesClient kubernetesClient(KubernetesProperties properties) {
    Config config;
    if (properties.kubeconfig().isBlank()) {
      config = Config.autoConfigure(null);
    } else {
      try {
        config = Config.fromKubeconfig(Files.readString(Path.of(properties.kubeconfig())));
      } catch (IOException e) {
        throw new UncheckedIOException("Cannot read kubeconfig " + properties.kubeconfig(), e);
      }
    }
    if (!properties.masterUrl().isBlank()) {
      config.setMasterUrl(properties.masterUrl());
    }
    return new KubernetesClientBuilder().withConfig(config).build();
  }

  @Bean
  KubernetesWorkloadRuntime kubernetesWorkloadRuntime(
      KubernetesClient client, KubernetesProperties properties, JsonMapper jsonMapper) {
    return new KubernetesWorkloadRuntime(client, properties, jsonMapper);
  }
}
