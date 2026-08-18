package com.platform.admin.dto;

import java.math.BigDecimal;
import java.util.Map;

public record AdminMetricsResponse(
        Map<String, Long> usersByRole,
        long totalAccounts,
        BigDecimal totalBalance,
        Map<String, Long> transactionsByStatus
) {
}
