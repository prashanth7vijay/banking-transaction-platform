-- Phase 4: transaction state machine.
--
-- NOTE ON NUMBERING: enterprise-development-plan.md originally earmarked V11 for
-- Phase 5's approvals_schema. This migration claims V11 instead, so Phase 5 will
-- use V12 - flagged here (and again when Phase 5 starts) rather than silently
-- drifting from the plan's stated numbering.

-- transaction_state_history: append-only log of every state transition, written
-- directly by TransactionStateMachine in the same DB transaction as the
-- transition itself. Backs the transactions/{id}/timeline endpoint. from_status
-- is nullable - the first row for a transaction records its creation, which has
-- no prior state to transition from.
CREATE TABLE transactions.transaction_state_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions.transactions (id),
    from_status VARCHAR(16),
    to_status VARCHAR(16) NOT NULL,
    actor_user_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_transaction_state_history_tx ON transactions.transaction_state_history (transaction_id, occurred_at);

-- The old flat status enum's PENDING value no longer exists as a
-- TransactionStatus constant as of this phase - any row still holding it (e.g. a
-- persisted docker volume from before this phase) would fail
-- @Enumerated(EnumType.STRING) deserialization on read. PENDING_APPROVAL is its
-- exact semantic successor (risk-checked, awaiting an approval decision), so
-- existing rows are migrated forward rather than left to break.
UPDATE transactions.transactions SET status = 'PENDING_APPROVAL' WHERE status = 'PENDING';

ALTER TABLE transactions.transactions ALTER COLUMN status SET DEFAULT 'SUBMITTED';
