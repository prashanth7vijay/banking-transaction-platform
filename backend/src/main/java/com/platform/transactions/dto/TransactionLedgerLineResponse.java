package com.platform.transactions.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionLedgerLineResponse(
        UUID accountId,
        String accountNumber,
        String direction,
        BigDecimal amount,
        String currency,
        Instant postedAt
) {
}
