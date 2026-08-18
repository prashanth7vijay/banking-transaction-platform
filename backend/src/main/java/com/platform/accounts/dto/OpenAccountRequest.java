package com.platform.accounts.dto;

import com.platform.accounts.domain.AccountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record OpenAccountRequest(
        @NotNull UUID customerUserId,
        @NotNull AccountType accountType,

        @NotNull
        @DecimalMin(value = "0.00", message = "Opening balance cannot be negative")
        BigDecimal openingBalance
) {
}
