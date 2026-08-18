package com.platform.transactions.port;

import com.platform.transactions.domain.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * What a module outside `transactions` is allowed to know about one
 * transaction - the {@code exceptions} module's investigation workspace needs
 * this to show case context without `exceptions` reaching into
 * TransactionRepository directly. Deliberately narrower than the full
 * {@code Transaction} entity - no direct entity leakage across module
 * boundaries, same convention as {@code AccountSummary}.
 */
public record TransactionSummary(
        UUID id,
        UUID sourceAccountId,
        String destinationAccountNumber,
        BigDecimal amount,
        String currency,
        TransactionStatus status,
        UUID initiatedByUserId,
        Instant createdAt
) {
}
