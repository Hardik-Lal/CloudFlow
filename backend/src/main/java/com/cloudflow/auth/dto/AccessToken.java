package com.cloudflow.auth.dto;

/** A signed access token and its lifetime in seconds. */
public record AccessToken(String value, long expiresInSeconds) {}
