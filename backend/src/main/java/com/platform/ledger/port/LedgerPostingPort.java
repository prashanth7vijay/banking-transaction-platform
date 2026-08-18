package com.platform.ledger.port;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The `ledger` module's public contract for other modules - `transactions`
 * depends on this only, never on LedgerAccountRepository or the LedgerService's
 * lower-level posting mechanics directly. Same module-boundary pattern as
 * AccountLookupPort/UserLookupPort.
 */
public interface LedgerPostingPort {

    /**
     * Posts a balanced DEBIT (source) / CREDIT (destination) entry for a completed
     * transfer, translating the two bank-account IDs into their corresponding
     * ledger accounts internally. Throws if either side has no ledger coverage
     * (should not happen given Phase 1's listener + backfill, but this is not
     * silently swallowed - a missing ledger account is a real integrity problem,
     * not something to paper over).
     */
    void postTransfer(UUID transactionId, UUID sourceAccountId, UUID destinationAccountId,
                       BigDecimal amount, String currency, String description);

    /**
     * The journal lines posted for one transaction (empty if none - e.g. the
     * transaction never reached COMPLETED). For Transaction 360's ledger section -
     * a read-only sibling of {@link #postTransfer}, not a new query surface for
     * `transactions` to build its own ledger logic on top of.
     */
    List<JournalLineSummary> getJournalLinesForTransaction(UUID transactionId);
}
