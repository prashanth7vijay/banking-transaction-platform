package com.platform.exceptions.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionExceptionResponse(
        UUID id,
        UUID transactionId,
        BigDecimal amount,
        String currency,
        String status,
        String priority,
        String reason,
        PartyResponse assignedTo,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt,
        Instant slaDueAt,
        /** WITHIN, AT_RISK, BREACHED (open cases) or MET, BREACHED (resolved/closed cases) - computed at read time, never stored. */
        String slaStatus
) {
}
