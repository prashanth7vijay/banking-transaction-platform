package com.platform.transactions.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One item in the Approval Workbench - everything an approver needs to decide
 * without navigating away: the transfer itself, the risk picture, a plain-
 * language explanation of why it's here, how it compares to the customer's
 * usual activity, and the SLA clock. Composed by {@code ApprovalWorkbenchService}
 * from data `transactions` and `risk` already have - nothing here is invented.
 */
public record ApprovalWorkbenchItemResponse(
        UUID transactionId,
        BigDecimal amount,
        String currency,
        String destinationAccountNumber,
        TransactionPartyResponse customer,
        String riskLevel,
        List<String> riskReasons,
        /** Plain-language reasons this item is in the queue - always includes the baseline maker-checker reason, plus any risk signals and notable-amount comparisons. */
        List<String> whyApprovalRequired,
        /** The customer's average COMPLETED transfer amount from this account, excluding this one. Null if they have no completed transfer history yet. */
        BigDecimal customerAverageAmount,
        /** amount / customerAverageAmount, when computable. */
        Double amountToAverageRatio,
        Instant createdAt,
        Instant slaDueAt,
        /** WITHIN, AT_RISK, or BREACHED - computed at read time from slaDueAt and the window implied by createdAt, not stored. */
        String slaStatus,
        String note
) {
}
