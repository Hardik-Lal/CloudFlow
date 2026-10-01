package com.cloudflow.storage.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Artifact storage in Amazon S3 or an S3-compatible service.
 *
 * @param enabled store artifacts; when off, nothing is archived and the artifact list stays empty
 * @param bucket bucket that holds all artifacts (created by the operator)
 * @param region AWS region of the bucket
 * @param endpoint custom endpoint for S3-compatible storage; empty uses AWS S3
 * @param pathStyleAccess address buckets as {@code endpoint/bucket} (needed by most S3-compatible
 *     services)
 * @param accessKey access key id; empty uses the default AWS credential chain (for example the EC2
 *     instance role)
 * @param secretKey secret access key
 * @param timeout upper bound for one storage call
 * @param maxObjectSizeMb artifacts larger than this are not stored
 */
@Validated
@ConfigurationProperties("cloudflow.storage")
public record StorageProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("cloudflow-artifacts") @NotBlank String bucket,
    @DefaultValue("us-east-1") @NotBlank String region,
    @DefaultValue("") String endpoint,
    @DefaultValue("false") boolean pathStyleAccess,
    @DefaultValue("") String accessKey,
    @DefaultValue("") String secretKey,
    @DefaultValue("30s") @NotNull Duration timeout,
    @DefaultValue("50") int maxObjectSizeMb) {}
