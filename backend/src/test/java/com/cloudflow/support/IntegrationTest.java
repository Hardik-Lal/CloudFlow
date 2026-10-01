package com.cloudflow.support;

import com.cloudflow.TestcontainersConfiguration;
import com.cloudflow.assistant.client.AiServiceClient;
import com.cloudflow.deployment.engine.ContainerRuntime;
import com.cloudflow.deployment.engine.HealthChecker;
import com.cloudflow.deployment.engine.ImageScanner;
import com.cloudflow.github.client.GithubClient;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Full application context against a real PostgreSQL (Testcontainers) with MockMvc. All integration
 * tests share one cached context and database, so tests create uniquely named data.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestUsers.class, TestApi.class, TestProjects.class})
@MockitoBean(
    types = {
      GithubClient.class,
      ContainerRuntime.class,
      HealthChecker.class,
      ImageScanner.class,
      AiServiceClient.class
    })
public @interface IntegrationTest {}
