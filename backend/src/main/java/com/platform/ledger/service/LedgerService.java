package com.platform.ledger.service;

import com.platform.ledger.domain.EntryDirection;
import com.platform.ledger.domain.JournalEntry;
import com.platform.ledger.domain.JournalEntryLine;
import com.platform.ledger.domain.LedgerAccount;
import com.platform.ledger.domain.SystemLedgerAccounts;
import com.platform.ledger.exception.UnbalancedJournalEntryException;
import com.platform.ledger.repository.JournalEntryRepository;
import com.platform.ledger.repository.LedgerAccountRepository;
import com.platform.shared.exception.ResourceNotFoundException;
import com.platform.shared.web.CorrelationIdFilter;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The one place in the codebase that posts to the ledger. Not called by anything
 * yet in this phase (Phase 1 of the enterprise-evolution plan) - ApprovalService
 * starts calling this in Phase 2's dual-write. Shipping it now, fully functional
 * and tested, keeps the two phases independently reviewable: this phase is "the
 * posting primitive is correct," the next is "the money-movement code path now
 * uses it."
 * <p>
 * Hybrid design: the ledger (journal_entries/journal_entry_lines) is the source
 * of truth; ledger_accounts.ledger_balance is a materialized projection updated
 * incrementally in the same transaction as the posting, for the same reason
 * accounts.accounts.balance is materialized rather than recomputed on every read -
 * balance reads are the overwhelmingly common case and need to be fast. The two
 * numbers can only disagree through a bug (never through a race - both updates
 * happen in the one transaction this method runs in), which is exactly what the
 * reconciliation job (a later phase) exists to continuously catch.
 */
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final JournalEntryRepository journalEntryRepository;
    private final LedgerAccountRepository ledgerAccountRepository;

    public record LineRequest(UUID ledgerAccountId, EntryDirection direction, BigDecimal amount, String currency) {
    }

    /**
     * Posts a balanced journal entry. Throws {@link UnbalancedJournalEntryException}
     * if the lines don't sum to zero (debits minus credits) per currency - this
     * check runs before anything is persisted, so a rejected entry leaves no trace
     * at all, not a partially-posted one.
     */
    @Transactional
    public JournalEntry post(UUID transactionReference, String description, String correlationId, List<LineRequest> lines) {
        assertBalanced(lines);

        JournalEntry entry = new JournalEntry();
        entry.setTransactionReference(transactionReference);
        entry.setDescription(description);
        entry.setAccountingDate(LocalDate.now());
        entry.setCorrelationId(correlationId != null ? correlationId : MDC.get(CorrelationIdFilter.MDC_KEY));

        for (LineRequest lineRequest : lines) {
            JournalEntryLine line = new JournalEntryLine();
            line.setJournalEntry(entry);
            line.setLedgerAccountId(lineRequest.ledgerAccountId());
            line.setDirection(lineRequest.direction());
            line.setAmount(lineRequest.amount());
            line.setCurrency(lineRequest.currency());
            entry.getLines().add(line);

            applyToMaterializedBalance(lineRequest);
        }

        return journalEntryRepository.save(entry);
    }

    /**
     * Debit decreases a ledger account's balance, credit increases it - the
     * retail-banking-statement convention (debit = money out, credit = money in),
     * not the double-entry-asset-account convention, since ledger_accounts models
     * each customer's own balance from their perspective, exactly like
     * accounts.accounts already does.
     */
    private void applyToMaterializedBalance(LineRequest lineRequest) {
        LedgerAccount ledgerAccount = ledgerAccountRepository.findById(lineRequest.ledgerAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Ledger account not found"));

        BigDecimal delta = lineRequest.direction() == EntryDirection.CREDIT
                ? lineRequest.amount()
                : lineRequest.amount().negate();

        ledgerAccount.setLedgerBalance(ledgerAccount.getLedgerBalance().add(delta));
        ledgerAccount.setAvailableBalance(ledgerAccount.getAvailableBalance().add(delta));
        ledgerAccountRepository.save(ledgerAccount);
    }

    private void assertBalanced(List<LineRequest> lines) {
        if (lines.isEmpty()) {
            throw new UnbalancedJournalEntryException("A journal entry must have at least one line");
        }

        Map<String, BigDecimal> netByCurrency = lines.stream()
                .collect(Collectors.groupingBy(
                        LineRequest::currency,
                        Collectors.reducing(BigDecimal.ZERO,
                                l -> l.direction() == EntryDirection.CREDIT ? l.amount() : l.amount().negate(),
                                BigDecimal::add)
                ));

        for (Map.Entry<String, BigDecimal> net : netByCurrency.entrySet()) {
            if (net.getValue().compareTo(BigDecimal.ZERO) != 0) {
                throw new UnbalancedJournalEntryException(
                        "Journal entry is not balanced for currency " + net.getKey() + ": net " + net.getValue());
            }
        }
    }

    @Transactional
    public LedgerAccount openLedgerAccount(UUID accountId, String currency) {
        if (ledgerAccountRepository.existsByAccountId(accountId)) {
            throw new IllegalStateException("A ledger account already exists for account " + accountId);
        }
        LedgerAccount ledgerAccount = new LedgerAccount();
        ledgerAccount.setAccountId(accountId);
        ledgerAccount.setCurrency(currency);
        return ledgerAccountRepository.save(ledgerAccount);
    }

    public boolean hasLedgerAccount(UUID accountId) {
        return ledgerAccountRepository.existsByAccountId(accountId);
    }

    /**
     * Opens a ledger account and, if the opening balance is non-zero, posts a
     * balanced entry crediting it against the system opening-balance clearing
     * account. A zero opening balance posts no entry at all - journal_entry_lines
     * has a CHECK (amount > 0) constraint, so a zero-value line is never
     * constructible, not just conventionally avoided.
     */
    @Transactional
    public LedgerAccount openLedgerAccountWithOpeningBalance(UUID accountId, String currency,
                                                              BigDecimal openingBalance, String description) {
        LedgerAccount ledgerAccount = openLedgerAccount(accountId, currency);

        if (openingBalance.compareTo(BigDecimal.ZERO) > 0) {
            LedgerAccount clearingAccount = getOrCreateSystemAccount(SystemLedgerAccounts.OPENING_BALANCE_CLEARING, currency);
            post(null, description, null, List.of(
                    new LineRequest(ledgerAccount.getId(), EntryDirection.CREDIT, openingBalance, currency),
                    new LineRequest(clearingAccount.getId(), EntryDirection.DEBIT, openingBalance, currency)
            ));
        }

        return ledgerAccount;
    }

    private LedgerAccount getOrCreateSystemAccount(UUID sentinelAccountId, String currency) {
        return ledgerAccountRepository.findByAccountId(sentinelAccountId)
                .orElseGet(() -> openLedgerAccount(sentinelAccountId, currency));
    }
}