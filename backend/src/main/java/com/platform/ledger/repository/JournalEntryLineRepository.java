package com.platform.ledger.repository;

import com.platform.ledger.domain.JournalEntryLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JournalEntryLineRepository extends JpaRepository<JournalEntryLine, UUID> {
    List<JournalEntryLine> findByJournalEntryId(UUID journalEntryId);
    List<JournalEntryLine> findByLedgerAccountIdOrderByCreatedAtAsc(UUID ledgerAccountId);
}
