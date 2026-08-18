package com.platform.dashboard.dto;

import com.platform.exceptions.port.ExceptionMetricsSummary;
import com.platform.transactions.port.TransactionMetricsSummary;

/**
 * The Operations Command Center's one read. Composes two independently-owned
 * metrics ports - `transactions` and `exceptions` - the same "aggregator, not
 * a new source of truth" pattern as `Transaction360Service` and
 * `ApprovalWorkbenchService`. `dashboard` owns no tables of its own.
 */
public record CommandCenterSummaryResponse(
        TransactionMetricsSummary transactions,
        ExceptionMetricsSummary exceptions
) {
}
