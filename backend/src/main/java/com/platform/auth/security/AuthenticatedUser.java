package com.platform.auth.security;

import java.util.Set;
import java.util.UUID;

/**
 * The authenticated principal derived directly from JWT claims - no DB hit on
 * every request. Roles are re-synced from the database on token refresh, so a
 * revoked role takes effect within one access-token lifetime.
 */
public record AuthenticatedUser(
        UUID userId,
        String email,
        Set<String> roles
) {
}
