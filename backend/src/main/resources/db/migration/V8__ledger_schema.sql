CREATE SCHEMA IF NOT EXISTS ledger;

CREATE TABLE ledger.ledger_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    -- Points at accounts.accounts.id by convention for real customer accounts, or
    -- at a well-known system sentinel (e.g. the opening-balance clearing account) -
    -- a plain UUID column, no cross-schema FK, same tradeoff this codebase already
    -- makes everywhere else (see accounts.accounts.user_id).
    account_id UUID NOT NULL UNIQUE,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    ledger_balance NUMERIC(19,4) NOT NULL DEFAULT 0,
    available_balance NUMERIC(19,4) NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ledger.journal_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_reference UUID,
    description VARCHAR(255) NOT NULL,
    accounting_date DATE NOT NULL,
    posted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    correlation_id VARCHAR(64)
);

CREATE TABLE ledger.journal_entry_lines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    journal_entry_id UUID NOT NULL REFERENCES ledger.journal_entries (id),
    ledger_account_id UUID NOT NULL REFERENCES ledger.ledger_accounts (id),
    direction VARCHAR(6) NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_journal_lines_entry ON ledger.journal_entry_lines (journal_entry_id);
CREATE INDEX idx_journal_lines_account ON ledger.journal_entry_lines (ledger_account_id);
CREATE INDEX idx_journal_entries_tx_ref ON ledger.journal_entries (transaction_reference);
CREATE INDEX idx_journal_entries_accounting_date ON ledger.journal_entries (accounting_date);
