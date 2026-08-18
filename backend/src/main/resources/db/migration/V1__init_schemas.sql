-- Baseline migration: one schema per module, matching the modular-monolith boundaries.
-- Tables land in these schemas starting with the users/auth migrations in Phase 1.

CREATE SCHEMA IF NOT EXISTS users;
CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS accounts;
CREATE SCHEMA IF NOT EXISTS transactions;
CREATE SCHEMA IF NOT EXISTS audit;
CREATE SCHEMA IF NOT EXISTS notifications;
