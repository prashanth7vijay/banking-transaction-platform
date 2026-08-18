package com.platform.transactions.dto;

import com.platform.transactions.domain.TransactionStatus;
import com.platform.transactions.domain.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        UUID sourceAccountId,
        String destinationAccountNumber,
        BigDecimal amount,
        String currency,
        TransactionStatus status,
        TransactionType type,
        String note,
        Instant createdAt,
        Instant approvedAt,
        String riskLevel
) {
}
