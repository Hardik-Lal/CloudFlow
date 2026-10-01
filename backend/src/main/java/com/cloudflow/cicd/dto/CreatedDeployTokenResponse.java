package com.cloudflow.cicd.dto;

/**
 * Returned once, when a token is created. The raw {@code token} cannot be retrieved again.
 *
 * @param secretName the GitHub repository secret the generated workflow reads the token from
 */
public record CreatedDeployTokenResponse(
    DeployTokenResponse deployToken, String token, String secretName) {}
