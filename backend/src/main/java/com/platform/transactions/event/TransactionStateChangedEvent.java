package com.platform.transactions.event;

import com.platform.transactions.domain.TransactionStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * A direct generalization of {@link TransactionStatusChangedEvent}, published by
 * {@link com.platform.transactions.service.TransactionStateMachine} on every single
 * legal transition (not just the handful of "final" statuses the older event fires
 * on). Kept as an additive new type rather than changing the older event's shape -
 * existing listeners (audit, notifications) that only care about the four original
 * outcome statuses keep working completely unmodified; new listeners that need the
 * richer per-edge picture (reconciliation, opsexceptions - both later phases)
 * subscribe to this one instead.
 * <p>
 * No listener subscribes to this event yet as of Phase 4 - flagged here rather than
 * silently omitted. It exists now because {@code TransactionStateMachine} is the
 * one place transitions happen, so publishing it here costs nothing and needs no
 * later retrofit; {@code transactions/{id}/timeline} is served directly from the
 * persisted {@code TransactionStateHistory} table this phase, not by replaying
 * this event, since events aren't queryable after the fact.
 */
public record TransactionStateChangedEvent(
        UUID transactionId,
        TransactionStatus fromStatus,
        TransactionStatus toStatus,
        UUID actorUserId,
        Instant occurredAt
) {
}
