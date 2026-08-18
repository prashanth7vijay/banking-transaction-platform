package com.platform.users.port;

import java.util.UUID;

/**
 * The `users` module's public contract for other modules that only need to
 * resolve basic recipient info (e.g. `notifications` sending an email) - never
 * the full `User` entity or `UserRepository`.
 */
public interface UserLookupPort {
    UserSummary getById(UUID userId);
}
