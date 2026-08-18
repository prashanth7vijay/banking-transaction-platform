package com.platform.risk.service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Deliberately not a live handle back into the database - a RiskStrategy
 * implementation gets exactly the data it needs and nothing more, so it can
 * never accidentally cause side effects or an N+1 query.
 */
public record TransferContext(
        UUID sourceAccountId,
        UUID customerUserId,
        BigDecimal amount,
        String currency,
        BigDecimal todaysAmountSoFar,
        long recentTransferCount
) {
}
