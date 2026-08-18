package com.platform.ledger.repository;

import com.platform.ledger.domain.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {
    List<JournalEntry> findByTransactionReferenceOrderByPostedAtAsc(UUID transactionReference);
}
