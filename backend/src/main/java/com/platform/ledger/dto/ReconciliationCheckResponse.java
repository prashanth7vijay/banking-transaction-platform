package com.platform.ledger.dto;

import java.util.List;

public record ReconciliationCheckResponse(
        int accountsChecked,
        List<BalanceMismatch> mismatches
) {
}
