package com.platform.ledger.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record BalanceMismatch(
        UUID accountId,
        BigDecimal ledgerBalance,
        BigDecimal actualBalance,
        BigDecimal difference
) {
}
