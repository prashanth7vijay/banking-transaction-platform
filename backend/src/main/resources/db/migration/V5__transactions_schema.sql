CREATE TABLE transactions.transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_account_id UUID NOT NULL,
    destination_account_number VARCHAR(20) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    type VARCHAR(16) NOT NULL DEFAULT 'TRANSFER',
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    initiated_by_user_id UUID NOT NULL,
    approved_by_user_id UUID,
    approved_at TIMESTAMPTZ,
    note VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_transactions_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_transactions_source_account ON transactions.transactions (source_account_id);
CREATE INDEX idx_transactions_status ON transactions.transactions (status);
CREATE INDEX idx_transactions_initiated_by ON transactions.transactions (initiated_by_user_id);
