package com.platform.transactions.port;

import java.math.BigDecimal;

public record TransactionMetricsSummary(
        long totalToday,
        BigDecimal totalValueToday,
        long completedToday,
        long failedToday,
        long pendingApprovalCount,
        long highRiskPendingCount,
        /** completedToday / (completedToday + failedToday + rejectedToday). Null if nothing was decided today yet. */
        Double successRateToday,
        /** Average minutes from creation to an approval decision, across all decided transactions ever. Null if none decided yet. */
        Double averageApprovalMinutes,
        /** Among decided transactions that had an SLA at all, the % decided before their deadline. Null if none. */
        Double slaComplianceRate,
        long slaWithinCount,
        long slaAtRiskCount,
        long slaBreachedCount
) {
}
