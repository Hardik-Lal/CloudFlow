package com.cloudflow.deployment.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Kubernetes as a deployment target (Phase 10).
 *
 * @param enabled whether environments may choose the Kubernetes target
 * @param kubeconfig path to a kubeconfig file; empty uses the standard discovery (KUBECONFIG,
 *     ~/.kube/config, or the in-cluster service account)
 * @param masterUrl overrides the API server URL from the kubeconfig (e.g. when the kubeconfig names
 *     127.0.0.1 but the backend reaches the cluster by another host name)
 * @param namespace namespace that application workloads are created in
 * @param nodeHost host the backend uses to reach NodePort services for health probes
 * @param publicHost host in the URLs of applications deployed to Kubernetes
 * @param storageClass storage class of application data volumes; empty uses the cluster default
 * @param volumeSize size of each environment's data volume
 * @param startTimeout how long a new pod may take to be scheduled and start its container
 */
@Validated
@ConfigurationProperties("cloudflow.kubernetes")
public record KubernetesProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("") String kubeconfig,
    @DefaultValue("") String masterUrl,
    @DefaultValue("cloudflow-apps") @NotBlank String namespace,
    @DefaultValue("localhost") @NotBlank String nodeHost,
    @DefaultValue("localhost") @NotBlank String publicHost,
    @DefaultValue("") String storageClass,
    @DefaultValue("1Gi") @NotBlank String volumeSize,
    @DefaultValue("5m") @NotNull Duration startTimeout) {}
