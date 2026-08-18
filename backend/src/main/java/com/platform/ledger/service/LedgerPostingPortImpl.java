package com.platform.ledger.service;

import com.platform.ledger.domain.EntryDirection;
import com.platform.ledger.domain.JournalEntry;
import com.platform.ledger.domain.JournalEntryLine;
import com.platform.ledger.domain.LedgerAccount;
import com.platform.ledger.port.JournalLineSummary;
import com.platform.ledger.port.LedgerPostingPort;
import com.platform.ledger.repository.JournalEntryLineRepository;
import com.platform.ledger.repository.JournalEntryRepository;
import com.platform.ledger.repository.LedgerAccountRepository;
import com.platform.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LedgerPostingPortImpl implements LedgerPostingPort {

    private final LedgerAccountRepository ledgerAccountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final JournalEntryLineRepository journalEntryLineRepository;
    private final LedgerService ledgerService;

    @Override
    @Transactional
    public void postTransfer(UUID transactionId, UUID sourceAccountId, UUID destinationAccountId,
                              BigDecimal amount, String currency, String description) {
        LedgerAccount source = ledgerAccountRepository.findByAccountId(sourceAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("No ledger account for source account " + sourceAccountId));
        LedgerAccount destination = ledgerAccountRepository.findByAccountId(destinationAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("No ledger account for destination account " + destinationAccountId));

        ledgerService.post(transactionId, description, null, List.of(
                new LedgerService.LineRequest(source.getId(), EntryDirection.DEBIT, amount, currency),
                new LedgerService.LineRequest(destination.getId(), EntryDirection.CREDIT, amount, currency)
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public List<JournalLineSummary> getJournalLinesForTransaction(UUID transactionId) {
        List<JournalEntry> entries = journalEntryRepository.findByTransactionReferenceOrderByPostedAtAsc(transactionId);
        if (entries.isEmpty()) {
            return List.of();
        }

        // One batch lookup per journal entry's lines, then one batch lookup to
        // translate every ledger_account_id -> bank account_id in a single query,
        // rather than N+1 round trips per line.
        List<JournalEntryLine> allLines = new ArrayList<>();
        for (JournalEntry entry : entries) {
            allLines.addAll(journalEntryLineRepository.findByJournalEntryId(entry.getId()));
        }

        Map<UUID, UUID> ledgerAccountIdToBankAccountId = ledgerAccountRepository
                .findAllById(allLines.stream().map(JournalEntryLine::getLedgerAccountId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(LedgerAccount::getId, LedgerAccount::getAccountId));

        Map<UUID, JournalEntry> entryById = entries.stream().collect(Collectors.toMap(JournalEntry::getId, e -> e));

        return allLines.stream()
                .map(line -> {
                    JournalEntry entry = entryById.get(line.getJournalEntry().getId());
                    return new JournalLineSummary(
                            entry.getId(),
                            ledgerAccountIdToBankAccountId.get(line.getLedgerAccountId()),
                            line.getDirection().name(),
                            line.getAmount(),
                            line.getCurrency(),
                            entry.getDescription(),
                            entry.getPostedAt()
                    );
                })
                .toList();
    }
}
