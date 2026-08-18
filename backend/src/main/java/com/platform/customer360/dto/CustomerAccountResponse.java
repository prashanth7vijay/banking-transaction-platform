package com.platform.customer360.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CustomerAccountResponse(
        UUID id,
        String accountNumber,
        BigDecimal balance,
        String status
) {
}
