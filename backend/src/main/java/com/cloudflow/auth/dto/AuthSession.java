package com.cloudflow.auth.dto;

/** Outcome of a successful refresh: the API response body and the rotated refresh token. */
public record AuthSession(AuthTokenResponse response, String refreshToken) {}
