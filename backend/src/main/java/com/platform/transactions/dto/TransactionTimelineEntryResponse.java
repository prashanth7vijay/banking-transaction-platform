package com.platform.transactions.dto;

import com.platform.transactions.domain.TransactionStatus;

import java.time.Instant;
import java.util.UUID;

public record TransactionTimelineEntryResponse(
        TransactionStatus fromStatus,
        TransactionStatus toStatus,
        UUID actorUserId,
        Instant occurredAt
) {
}
