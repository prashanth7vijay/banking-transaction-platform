package com.platform.ledger.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "journal_entry_lines", schema = "ledger")
@Getter
@Setter
@NoArgsConstructor
public class JournalEntryLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id", nullable = false)
    private JournalEntry journalEntry;

    @Column(name = "ledger_account_id", nullable = false)
    private UUID ledgerAccountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private EntryDirection direction;

    /**
     * Always positive - direction (DEBIT/CREDIT) carries the sign, not the amount
     * itself. This is what makes "debits = credits" a plain sum-and-compare check
     * in LedgerService rather than something that needs sign-flipping logic.
     */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
