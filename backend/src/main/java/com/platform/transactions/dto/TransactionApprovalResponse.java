package com.platform.transactions.dto;

import java.time.Instant;

public record TransactionApprovalResponse(
        TransactionPartyResponse approvedBy,
        Instant approvedAt,
        String decision
) {
}
