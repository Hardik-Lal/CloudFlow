package com.cloudflow.deployment.engine;

import com.cloudflow.environment.domain.DeploymentTarget;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Everything needed to start an application container.
 *
 * @param target where the workload runs
 * @param network Docker network (Docker target only)
 * @param volumeName named volume mounted at {@code volumePath}; persists across deployments
 */
public record ContainerSpec(
    DeploymentTarget target,
    String name,
    String imageTag,
    Map<String, String> environment,
    int containerPort,
    String network,
    String volumeName,
    String volumePath,
    BigDecimal cpuLimit,
    Integer memoryLimitMb,
    Map<String, String> labels) {}
