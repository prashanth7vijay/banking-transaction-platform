-- Feature 3: Approval Workbench + SLA.
--
-- sla_due_at is set once, at the moment a transaction enters PENDING_APPROVAL
-- (TransferService.createTransfer), from a fixed risk-level-based window - not
-- recomputed on every read. Nullable: a transaction that never reached
-- PENDING_APPROVAL (e.g. blocked by risk straight to FAILED) never gets one.

ALTER TABLE transactions.transactions ADD COLUMN sla_due_at TIMESTAMPTZ;
