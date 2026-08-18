package com.platform.ledger.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Append-only by convention, same as audit.audit_logs: no service method in this
 * codebase ever issues an UPDATE or DELETE against a JournalEntry or its lines. A
 * mistaken posting is corrected with a reversing entry (equal and opposite
 * lines), never an edit - which is also why this entity deliberately has no
 * updatedAt column at all; its absence is the immutability signal.
 */
@Entity
@Table(name = "journal_entries", schema = "ledger")
@Getter
@Setter
@NoArgsConstructor
public class JournalEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * What real-world event caused this entry. Points at transactions.transactions.id
     * for a transfer; nullable to allow future non-transfer postings (fees, interest,
     * the opening-balance backfill entry).
     */
    @Column(name = "transaction_reference")
    private UUID transactionReference;

    @Column(nullable = false, length = 255)
    private String description;

    /**
     * The business date this entry books to - separate from postedAt because real
     * accounting periodically needs a posting's accounting date to differ from its
     * literal insert timestamp. Not used by any flow yet; included now so it's not
     * a migration later.
     */
    @Column(name = "accounting_date", nullable = false)
    private LocalDate accountingDate;

    @CreationTimestamp
    @Column(name = "posted_at", nullable = false, updatable = false)
    private Instant postedAt;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @OneToMany(mappedBy = "journalEntry", cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    private List<JournalEntryLine> lines = new ArrayList<>();
}
