package com.cloudflow.assistant.config;

import java.time.Duration;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class AiClientConfig {

  /** Background indexing of finished deployments; small and bounded, drops work when saturated. */
  @Bean
  ThreadPoolTaskExecutor aiTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("ai-index-");
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(2);
    executor.setQueueCapacity(100);
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardOldestPolicy());
    executor.initialize();
    return executor;
  }

  @Bean
  RestClient aiRestClient(RestClient.Builder builder, AiServiceProperties properties) {
    HttpClientSettings settings =
        HttpClientSettings.defaults()
            .withConnectTimeout(Duration.ofSeconds(5))
            .withReadTimeout(properties.timeout());
    return builder
        .baseUrl(properties.baseUrl())
        .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
        .defaultHeader("X-Internal-Token", properties.internalToken())
        .build();
  }
}
