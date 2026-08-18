package com.platform.exceptions.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A case opened against a transaction that needs operational attention - today
 * that means every transaction that reaches {@code FAILED}
 * ({@link com.platform.exceptions.listener.ExceptionAutoCreationListener} does
 * this automatically), but the model doesn't assume that's the only source; a
 * later phase could open one for a reconciliation mismatch or a
 * customer-reported issue the same way.
 * <p>
 * {@code transactionId} deliberately has no JPA association and no DB-level
 * foreign key to {@code transactions.transactions} - same cross-schema
 * boundary every other module (risk, ledger) already respects. This entity
 * never mutates the transaction it references; corrective actions (e.g. retry)
 * create new records elsewhere instead.
 */
@Entity
@Table(name = "transaction_exceptions", schema = "exceptions")
@Getter
@Setter
@NoArgsConstructor
public class TransactionException {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExceptionStatus status = ExceptionStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ExceptionPriority priority;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "assigned_to_user_id")
    private UUID assignedToUserId;

    @Column(name = "sla_due_at", nullable = false)
    private Instant slaDueAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
