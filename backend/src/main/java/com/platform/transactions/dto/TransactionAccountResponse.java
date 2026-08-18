package com.platform.transactions.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TransactionAccountResponse(
        UUID id,
        String accountNumber,
        BigDecimal balance,
        String status,
        TransactionPartyResponse owner
) {
}
