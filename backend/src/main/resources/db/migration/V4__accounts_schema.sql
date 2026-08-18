CREATE TABLE accounts.accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    account_number VARCHAR(20) NOT NULL UNIQUE,
    account_type VARCHAR(16) NOT NULL,
    balance NUMERIC(19,4) NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_accounts_balance_non_negative CHECK (balance >= 0)
);

CREATE INDEX idx_accounts_user_id ON accounts.accounts (user_id);

CREATE TABLE accounts.beneficiaries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id UUID NOT NULL,
    beneficiary_account_number VARCHAR(20) NOT NULL,
    nickname VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_beneficiary_owner_account UNIQUE (owner_user_id, beneficiary_account_number)
);

CREATE INDEX idx_beneficiaries_owner ON accounts.beneficiaries (owner_user_id);
