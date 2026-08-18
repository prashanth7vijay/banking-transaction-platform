package com.platform.ledger.config;

import com.platform.accounts.port.AccountLookupPort;
import com.platform.accounts.port.AccountSummary;
import com.platform.ledger.service.LedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Runs on every boot, in every profile - not gated to `dev` the way DataSeeder is,
 * since this needs to backfill real accounts too, not just demo ones. Safe to run
 * repeatedly: it only acts on accounts that don't already have ledger coverage
 * (LedgerService.hasLedgerAccount), so a restart never double-posts an opening
 * balance. Ordered after DataSeeder (@Order(1)) so demo accounts exist by the time
 * this runs in the dev profile; in any other profile there's no ordering
 * dependency since nothing else seeds accounts at boot.
 * <p>
 * Deliberately reaches `accounts` only through {@link AccountLookupPort} - never
 * AccountRepository directly - the same module-boundary rule every other
 * cross-module call in this codebase already follows.
 */
@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class LedgerBackfillRunner implements CommandLineRunner {

    private final AccountLookupPort accountLookupPort;
    private final LedgerService ledgerService;

    @Override
    public void run(String... args) {
        int backfilled = 0;
        for (AccountSummary account : accountLookupPort.findAll()) {
            if (ledgerService.hasLedgerAccount(account.id())) {
                continue;
            }
            ledgerService.openLedgerAccountWithOpeningBalance(
                    account.id(),
                    "USD",
                    account.balance(),
                    "Opening balance backfill for account " + account.id()
            );
            backfilled++;
        }
        if (backfilled > 0) {
            log.info("Ledger backfill: opened ledger coverage for {} pre-existing account(s)", backfilled);
        }
    }
}
