package com.cloudflow.auth.dto;

import java.util.UUID;

/** Result of exchanging a refresh token: the owning user and their replacement token. */
public record RefreshTokenRotation(UUID userId, String newRefreshToken) {}
