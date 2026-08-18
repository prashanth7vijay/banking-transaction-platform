package com.platform.ledger.listener;

import com.platform.accounts.event.AccountOpenedEvent;
import com.platform.ledger.service.LedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Gives every account opened from this point forward (via the employee-facing
 * account-opening flow) immediate ledger coverage - no separate backfill step
 * needed for new accounts, only for the ones that already existed before this
 * phase shipped (see LedgerBackfillRunner). Idempotent by construction: this
 * listener only ever fires once per real AccountOpenedEvent, and
 * LedgerService.openLedgerAccount() itself refuses to create a second ledger
 * account for the same accountId.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountOpenedLedgerListener {

    private final LedgerService ledgerService;

    @EventListener
    public void onAccountOpened(AccountOpenedEvent event) {
        if (ledgerService.hasLedgerAccount(event.accountId())) {
            // Defensive only - should not happen for a genuinely new account, but
            // guards against a future accidental double-publish of this event.
            return;
        }
        ledgerService.openLedgerAccountWithOpeningBalance(
                event.accountId(),
                "USD",
                event.openingBalance(),
                "Opening balance for account " + event.accountId()
        );
        log.info("Opened ledger coverage for account {} with opening balance {}",
                event.accountId(), event.openingBalance());
    }
}
