package com.platform.accounts.port;

import com.platform.accounts.domain.AccountStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountSummary(
        UUID id,
        UUID ownerUserId,
        String accountNumber,
        BigDecimal balance,
        AccountStatus status
) {
}
