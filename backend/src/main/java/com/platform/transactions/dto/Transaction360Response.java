package com.platform.transactions.dto;

import java.util.List;

/**
 * Composes what {@code transactions}, {@code risk}, and {@code ledger} already
 * separately know about one transaction into a single read - the "central
 * operational object" view. Nothing here is new business logic: every field is
 * either already on {@link com.platform.transactions.domain.Transaction},
 * already in {@code transaction_state_history} (Phase 4), or fetched through an
 * existing or newly-added read-only port method
 * ({@code RiskAssessmentPort#getAssessment}, {@code LedgerPostingPort#getJournalLinesForTransaction}).
 * {@code Transaction360Service} is purely an aggregator - it owns no data of its own.
 */
public record Transaction360Response(
        TransactionResponse transaction,
        TransactionPartyResponse customer,
        TransactionAccountResponse sourceAccount,
        TransactionAccountResponse destinationAccount,
        TransactionRiskResponse risk,
        TransactionApprovalResponse approval,
        List<TransactionLedgerLineResponse> ledgerLines,
        List<TransactionTimelineEntryResponse> timeline
) {
}
