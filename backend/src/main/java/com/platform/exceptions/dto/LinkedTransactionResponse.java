package com.platform.exceptions.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Just enough to show context and link out to Transaction 360 - not the full
 * view itself. Sourced from {@code TransactionLookupPort}, the same narrow
 * read port {@code ExceptionService} already depends on. The investigation
 * workspace page fetches the full {@code Transaction360Response} separately
 * (a second, independent API call) rather than this module embedding it -
 * see {@code ExceptionController}'s javadoc for why.
 */
public record LinkedTransactionResponse(
        UUID id,
        BigDecimal amount,
        String currency,
        String status,
        String destinationAccountNumber,
        Instant createdAt
) {
}
