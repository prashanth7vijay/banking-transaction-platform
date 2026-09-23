DROP INDEX IF EXISTS transactions.idx_transactions_initiated_by;

CREATE INDEX idx_transactions_customer_created_at
    ON transactions.transactions (initiated_by_user_id, created_at DESC);