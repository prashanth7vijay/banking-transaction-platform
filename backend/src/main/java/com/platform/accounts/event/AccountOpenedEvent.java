package com.platform.accounts.event;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountOpenedEvent(
        UUID accountId,
        UUID customerUserId,
        UUID openedByUserId,
        String accountType,
        BigDecimal openingBalance
) {
}
