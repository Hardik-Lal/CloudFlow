package com.cloudflow.common.security;

import java.util.UUID;

/**
 * The authenticated caller, resolved from the access token. Inject into controllers with
 * {@code @AuthenticationPrincipal AuthenticatedUser user}.
 */
public record AuthenticatedUser(UUID id, String username) {}
