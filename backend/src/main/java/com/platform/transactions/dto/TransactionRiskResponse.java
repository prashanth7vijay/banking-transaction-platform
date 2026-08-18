package com.platform.transactions.dto;

import java.time.Instant;
import java.util.List;

public record TransactionRiskResponse(
        String riskLevel,
        List<String> reasons,
        boolean blocked,
        Instant assessedAt
) {
}
