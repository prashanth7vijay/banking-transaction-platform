package com.platform.ledger.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One row per accounts.accounts row (a plain UUID reference, not a JPA
 * association - same cross-schema-FK tradeoff the rest of this codebase already
 * makes, see the accounts/transactions module boundary). ledger_balance is the
 * authoritative sum of every posted journal line for this account; the existing
 * accounts.accounts.balance column remains the fast-path materialized read and is
 * updated atomically alongside this row by LedgerService.post() - see the class
 * Javadoc there for the full hybrid-design rationale.
 */
@Entity
@Table(name = "ledger_accounts", schema = "ledger")
@Getter
@Setter
@NoArgsConstructor
public class LedgerAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_id", nullable = false, unique = true)
    private UUID accountId;

    @Column(nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "ledger_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal ledgerBalance = BigDecimal.ZERO;

    /**
     * ledgerBalance minus any active holds/reservations. No hold/reservation
     * concept exists yet anywhere in the transaction lifecycle, so this currently
     * just mirrors ledgerBalance - included now because adding the distinction
     * later would be a migration, not because it's used yet.
     */
    @Column(name = "available_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal availableBalance = BigDecimal.ZERO;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
