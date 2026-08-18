package com.platform.customer360.dto;

import java.time.Instant;
import java.util.UUID;

public record CustomerExceptionItem(
        UUID id,
        UUID transactionId,
        String status,
        String priority,
        String reason,
        String assignedToName,
        Instant createdAt
) {
}
