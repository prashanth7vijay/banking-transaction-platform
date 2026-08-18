CREATE SCHEMA IF NOT EXISTS risk;

CREATE TABLE risk.limit_policies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    scope VARCHAR(20) NOT NULL CHECK (scope IN ('GLOBAL', 'ACCOUNT_TYPE', 'CUSTOMER', 'ACCOUNT')),
    scope_reference UUID,
    limit_type VARCHAR(20) NOT NULL CHECK (limit_type IN ('PER_TRANSACTION', 'DAILY_CUMULATIVE', 'VELOCITY_COUNT')),
    max_amount NUMERIC(19,4),
    max_count INT,
    window_minutes INT,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_limit_policies_scope ON risk.limit_policies (scope, scope_reference) WHERE active;

CREATE TABLE risk.risk_assessments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL UNIQUE,
    risk_level VARCHAR(10) NOT NULL CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    reasons JSONB,
    blocked BOOLEAN NOT NULL DEFAULT false,
    assessed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_risk_assessments_tx ON risk.risk_assessments (transaction_id);
