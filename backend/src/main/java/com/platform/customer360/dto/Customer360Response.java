package com.platform.customer360.dto;

import java.util.List;

public record Customer360Response(
        CustomerProfileResponse profile,
        List<CustomerAccountResponse> accounts,
        CustomerTransactionActivityResponse transactionActivity,
        CustomerRiskProfileResponse risk,
        List<CustomerExceptionItem> openExceptions,
        List<CustomerTimelineEntry> timeline
) {
}
