package com.cloudflow.environment.dto;

import com.cloudflow.environment.domain.ConfigTemplate;

/**
 * @param recommended whether this template matches the requested application type
 */
public record ConfigTemplateResponse(
    ConfigTemplate template,
    String label,
    String description,
    boolean recommended,
    DeploymentConfigResponse defaults) {}
