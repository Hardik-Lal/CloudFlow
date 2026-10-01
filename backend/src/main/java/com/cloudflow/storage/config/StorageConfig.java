package com.cloudflow.storage.config;

import com.cloudflow.storage.service.ObjectStorage;
import com.cloudflow.storage.service.S3ObjectStorage;
import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

/** S3 client for artifact storage; only created when {@code cloudflow.storage.enabled}. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "cloudflow.storage.enabled", havingValue = "true")
public class StorageConfig {

  @Bean(destroyMethod = "close")
  S3Client artifactS3Client(StorageProperties properties) {
    S3ClientBuilder builder =
        S3Client.builder()
            .region(Region.of(properties.region()))
            .forcePathStyle(properties.pathStyleAccess())
            .overrideConfiguration(
                config ->
                    config
                        .apiCallTimeout(properties.timeout())
                        .apiCallAttemptTimeout(properties.timeout()));
    if (!properties.endpoint().isBlank()) {
      builder.endpointOverride(URI.create(properties.endpoint()));
    }
    builder.credentialsProvider(
        properties.accessKey().isBlank()
            ? DefaultCredentialsProvider.builder().build()
            : StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())));
    return builder.build();
  }

  @Bean
  ObjectStorage objectStorage(S3Client artifactS3Client, StorageProperties properties) {
    return new S3ObjectStorage(artifactS3Client, properties.bucket());
  }
}
