-- Exception & Investigation Management (Feature 2). A new module, own schema,
-- following the exact same cross-module conventions already established by
-- risk/ledger: transaction_id is a plain UUID with an index, never a real FK
-- across schemas (see risk.risk_assessments for the precedent) - modules stay
-- decoupled at the DB level too, not just in Java.

CREATE SCHEMA IF NOT EXISTS exceptions;

CREATE TABLE exceptions.transaction_exceptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'ASSIGNED', 'INVESTIGATING', 'ACTION_REQUIRED', 'RESOLVED', 'CLOSED')),
    priority VARCHAR(10) NOT NULL CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    reason VARCHAR(500) NOT NULL,
    assigned_to_user_id UUID,
    sla_due_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_transaction_exceptions_tx ON exceptions.transaction_exceptions (transaction_id);
CREATE INDEX idx_transaction_exceptions_status ON exceptions.transaction_exceptions (status);
CREATE INDEX idx_transaction_exceptions_assignee ON exceptions.transaction_exceptions (assigned_to_user_id);

-- Append-only, same convention as transaction_state_history and audit_logs: no
-- service method ever issues an UPDATE or DELETE against a row here. Covers
-- investigation notes, assignment changes, status changes, and recorded actions
-- (retry/escalate) - one unified chronological trail per exception.
CREATE TABLE exceptions.exception_notes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    exception_id UUID NOT NULL REFERENCES exceptions.transaction_exceptions (id),
    author_user_id UUID NOT NULL,
    note_type VARCHAR(20) NOT NULL CHECK (note_type IN ('NOTE', 'STATUS_CHANGE', 'ASSIGNMENT', 'ACTION_TAKEN')),
    content VARCHAR(2000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_exception_notes_exception ON exceptions.exception_notes (exception_id, created_at);
