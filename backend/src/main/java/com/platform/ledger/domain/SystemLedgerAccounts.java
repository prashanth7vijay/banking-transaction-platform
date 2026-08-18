package com.platform.ledger.domain;

import java.util.UUID;

/**
 * Well-known ledger accounts with no corresponding accounts.accounts row - the
 * accounting-equity/capital-account counterpart every opening-balance entry posts
 * against (a real bank's books need a source for money that "just appears" when
 * an account opens with a starting balance; this is that source). A fixed,
 * hardcoded ID is sufficient here since LedgerAccount.accountId is a plain,
 * unenforced UUID column by design (see AccountOpenedEvent listener / backfill),
 * not a foreign key - the same tradeoff the rest of this codebase already makes
 * for every cross-schema reference.
 */
public final class SystemLedgerAccounts {

    public static final UUID OPENING_BALANCE_CLEARING =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    private SystemLedgerAccounts() {
    }
}
