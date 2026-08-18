CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users.roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(32) NOT NULL UNIQUE
);

INSERT INTO users.roles (name) VALUES ('CUSTOMER'), ('EMPLOYEE'), ('ADMIN');

CREATE TABLE users.users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_users_email ON users.users (email);

CREATE TABLE users.user_roles (
    user_id UUID NOT NULL REFERENCES users.users (id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES users.roles (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);
