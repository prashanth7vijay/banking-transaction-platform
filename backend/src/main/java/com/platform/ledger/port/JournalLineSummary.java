package com.platform.ledger.port;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One journal entry line, translated back to the bank-account ID a caller
 * outside `ledger` actually understands ({@code accountId}, not the internal
 * {@code ledger_account_id}) - same translation LedgerPostingPortImpl already
 * does internally for postTransfer, just exposed for reads now. direction is a
 * plain "DEBIT"/"CREDIT" string rather than the {@code EntryDirection} enum, same
 * convention as RiskAssessmentPort.getRiskLevel returning a String - domain
 * enums stay internal to their own module.
 */
public record JournalLineSummary(
        UUID journalEntryId,
        UUID accountId,
        String direction,
        BigDecimal amount,
        String currency,
        String description,
        Instant postedAt
) {
}
