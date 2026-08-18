CREATE TABLE audit.audit_logs (
    id BIGSERIAL PRIMARY KEY,
    correlation_id VARCHAR(64),
    actor_user_id UUID,
    action VARCHAR(64) NOT NULL,
    entity_type VARCHAR(32) NOT NULL,
    entity_id VARCHAR(64),
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_logs_actor ON audit.audit_logs (actor_user_id);
CREATE INDEX idx_audit_logs_action ON audit.audit_logs (action);
CREATE INDEX idx_audit_logs_created_at ON audit.audit_logs (created_at DESC);
