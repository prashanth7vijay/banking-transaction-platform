package com.platform.customer360.dto;

import java.math.BigDecimal;
import java.util.List;

public record CustomerTransactionActivityResponse(
        List<CustomerTransactionItem> recentTransactions,
        long last30DayCount,
        BigDecimal last30DayValue,
        /** Average amount over the fetched (90-day) window - null if no transactions in that window. */
        BigDecimal averageTransactionAmount,
        long pendingCount,
        long failedCount,
        long highRiskCount
) {
}
