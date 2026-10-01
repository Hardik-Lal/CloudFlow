package com.cloudflow.deployment.config;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import java.net.URI;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
public class DeploymentEngineConfig {

  /** Created lazily by docker-java: no connection is made until the first call. */
  @Bean(destroyMethod = "close")
  DockerClient dockerClient(DeploymentProperties properties) {
    DefaultDockerClientConfig config =
        DefaultDockerClientConfig.createDefaultConfigBuilder()
            .withDockerHost(properties.dockerHost())
            .build();
    ApacheDockerHttpClient httpClient =
        new ApacheDockerHttpClient.Builder()
            .dockerHost(URI.create(properties.dockerHost()))
            .maxConnections(50)
            .connectionTimeout(Duration.ofSeconds(10))
            // Builds and log streams can be silent for a long time.
            .responseTimeout(properties.buildTimeout())
            .build();
    return DockerClientImpl.getInstance(config, httpClient);
  }

  /** Bounded pool: deployments beyond the queue capacity are rejected instead of piling up. */
  @Bean
  ThreadPoolTaskExecutor deploymentTaskExecutor(DeploymentProperties properties) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("deployment-");
    executor.setCorePoolSize(properties.concurrency());
    executor.setMaxPoolSize(properties.concurrency());
    executor.setQueueCapacity(properties.queueCapacity());
    executor.setWaitForTasksToCompleteOnShutdown(false);
    executor.initialize();
    return executor;
  }
}
