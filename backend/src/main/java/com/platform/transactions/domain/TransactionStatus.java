package com.platform.transactions.domain;

/**
 * The full transaction lifecycle (enterprise evolution Phase 4 / architecture doc
 * §6). Replaces the earlier flat four-value status: {@code PENDING} is gone,
 * replaced at creation time by {@link #SUBMITTED}, which the risk engine then
 * automatically advances to {@link #PENDING_APPROVAL} (or fails straight to
 * {@link #FAILED} on a hard block). Legality of every transition is owned in
 * exactly one place - {@link com.platform.transactions.service.TransactionStateMachine}
 * - not re-derived ad hoc by each service method.
 * <p>
 * {@link #COMPLETED}, {@link #REJECTED}, {@link #FAILED}, and {@link #CANCELLED}
 * are terminal: no code path may transition a transaction out of any of them.
 */
public enum TransactionStatus {
    /** Reserved for a future "save transfer for later" feature - not reachable by any flow today. */
    DRAFT,
    /** Transfer request received, not yet risk-evaluated. Replaces the old PENDING at creation. */
    SUBMITTED,
    /** Risk evaluation passed (or requires human judgment); routed to approval. */
    PENDING_APPROVAL,
    /** Final required approval recorded; funds have not moved yet. */
    APPROVED,
    /** Ledger-posting has been handed off. Modeled as its own state so a future async posting step needs no further state-machine redesign. */
    PROCESSING,
    /** Ledger posted, materialized balances updated. Terminal. */
    COMPLETED,
    /** An approver declined. Terminal. */
    REJECTED,
    /** Risk evaluation blocked it, or posting failed (e.g. insufficient funds re-checked at posting time). Terminal. */
    FAILED,
    /** The customer withdrew the request before a terminal decision. Terminal. No cancel endpoint exists yet - see TransactionStateMachine's transition table for the reserved edges. */
    CANCELLED
}
