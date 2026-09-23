package com.platform.users.port;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * The `users` module's public contract for other modules that only need to
 * resolve basic recipient info (e.g. `notifications` sending an email) - never
 * the full `User` entity or `UserRepository`.
 */
public interface UserLookupPort {
    UserSummary getById(UUID userId);

    /**
     * Batch first-name resolution for a set of user ids, keyed by id. Ids that
     * don't resolve to a user are simply absent from the map (no exception) -
     * this exists for optional display-only lookups like an exception's
     * assignee name, where "not found" should degrade gracefully rather than
     * fail the whole request. Callers that need a hard failure on a missing id
     * should keep using {@link #getById}.
     */
    Map<UUID, String> getFirstNamesByIds(Collection<UUID> userIds);
}
