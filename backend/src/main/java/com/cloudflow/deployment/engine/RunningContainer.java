package com.cloudflow.deployment.engine;

/**
 * @param hostPort port published on the Docker host, or {@code null} if none
 */
public record RunningContainer(String id, String name, Integer hostPort) {}
