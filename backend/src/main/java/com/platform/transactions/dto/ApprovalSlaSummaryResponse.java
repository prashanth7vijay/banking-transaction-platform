package com.platform.transactions.dto;

public record ApprovalSlaSummaryResponse(
        long within,
        long atRisk,
        long breached,
        long total
) {
}
