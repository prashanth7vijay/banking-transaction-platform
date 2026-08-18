package com.platform.customer360.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CustomerTransactionItem(
        UUID id,
        BigDecimal amount,
        String currency,
        String status,
        String destinationAccountNumber,
        Instant createdAt
) {
}
