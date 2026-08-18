package com.platform.accounts.dto;

import com.platform.accounts.domain.AccountStatus;
import com.platform.accounts.domain.AccountType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String accountNumber,
        AccountType accountType,
        BigDecimal balance,
        AccountStatus status,
        Instant createdAt
) {
}
