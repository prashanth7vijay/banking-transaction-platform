package com.platform.transactions.port;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The `transactions` module's public contract for `risk` - deliberately narrow:
 * two aggregate reads, nothing that exposes a `Transaction` entity or the
 * repository itself. Same module-boundary pattern as AccountLookupPort/
 * UserLookupPort/LedgerPostingPort.
 */
public interface TransferHistoryPort {

    /**
     * Sum of amounts already submitted or completed today from this account -
     * used for daily cumulative limit checks. Includes every in-flight status,
     * not just COMPLETED, so a customer can't bypass a daily cap by submitting
     * many transfers before any of them clear the approval queue.
     */
    BigDecimal sumTodaysAmountFromAccount(UUID accountId);

    /** Count of transfers submitted from this account in the last N minutes. */
    long countRecentTransfersFromAccount(UUID accountId, int windowMinutes);
}
