package com.platform.exceptions.port;

import java.time.Instant;
import java.util.UUID;

/**
 * What a module outside `exceptions` is allowed to know about one case -
 * deliberately narrower than the full {@code TransactionException} entity, and
 * returns a raw {@code assignedToUserId} rather than a resolved name (same
 * convention as every other Summary port record in this codebase - name
 * resolution is the caller's job via UserLookupPort, not this module's).
 */
public record ExceptionSummary(
        UUID id,
        UUID transactionId,
        String status,
        String priority,
        String reason,
        UUID assignedToUserId,
        Instant createdAt
) {
}
