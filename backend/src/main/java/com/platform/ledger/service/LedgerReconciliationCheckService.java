package com.platform.ledger.service;

import com.platform.accounts.port.AccountLookupPort;
import com.platform.accounts.port.AccountSummary;
import com.platform.ledger.domain.LedgerAccount;
import com.platform.ledger.dto.BalanceMismatch;
import com.platform.ledger.dto.ReconciliationCheckResponse;
import com.platform.ledger.repository.LedgerAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Deliberately manual/on-demand, not scheduled - the full scheduled reconciliation
 * job (with its own run/result tables, severity scoring, and automatic
 * OperationalException creation) is a later phase in the enterprise-evolution
 * plan. This exists now only to let Phase 2's dual-write be validated - "did
 * every approved transfer actually keep the ledger and the materialized balance
 * in agreement" - without waiting for that later phase to ship.
 */
@Service
@RequiredArgsConstructor
public class LedgerReconciliationCheckService {

    private final AccountLookupPort accountLookupPort;
    private final LedgerAccountRepository ledgerAccountRepository;

    @Transactional(readOnly = true)
    public ReconciliationCheckResponse checkAllBalances() {
        List<AccountSummary> accounts = accountLookupPort.findAll();
        List<BalanceMismatch> mismatches = new ArrayList<>();

        for (AccountSummary account : accounts) {
            Optional<LedgerAccount> ledgerAccount = ledgerAccountRepository.findByAccountId(account.id());
            if (ledgerAccount.isEmpty()) {
                mismatches.add(new BalanceMismatch(account.id(), BigDecimal.ZERO, account.balance(), account.balance()));
                continue;
            }

            BigDecimal difference = account.balance().subtract(ledgerAccount.get().getLedgerBalance());
            if (difference.compareTo(BigDecimal.ZERO) != 0) {
                mismatches.add(new BalanceMismatch(
                        account.id(), ledgerAccount.get().getLedgerBalance(), account.balance(), difference));
            }
        }

        return new ReconciliationCheckResponse(accounts.size(), mismatches);
    }
}
