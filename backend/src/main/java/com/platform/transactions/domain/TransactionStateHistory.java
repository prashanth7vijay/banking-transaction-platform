package com.platform.transactions.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only, one row per state transition a {@link Transaction} goes through.
 * Written directly by {@link com.platform.transactions.service.TransactionStateMachine}
 * inside the same DB transaction as the transition itself - this is the
 * authoritative, queryable source for the Transaction 360 timeline (the
 * {@code TransactionStateChangedEvent} published alongside it is for future
 * in-process listeners, e.g. reconciliation/opsexceptions in later phases; events
 * aren't queryable after the fact, so the timeline endpoint reads this table, not
 * the event bus).
 * <p>
 * {@code fromStatus} is nullable: the very first row for a transaction records its
 * creation (no prior state to transition from), not a state-machine-validated edge.
 */
@Entity
@Table(name = "transaction_state_history", schema = "transactions")
@Getter
@Setter
@NoArgsConstructor
public class TransactionStateHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 16)
    private TransactionStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 16)
    private TransactionStatus toStatus;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;
}
