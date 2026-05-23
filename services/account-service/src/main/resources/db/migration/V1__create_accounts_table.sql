-- account-service Phase 3 — Stage A: persistence baseline.
-- The accounts schema is created by infra/postgres/init.sql before Flyway runs.
-- Flyway tracks its own state in accounts.flyway_schema_history (see application.yml).

CREATE TABLE accounts.accounts (
    id            UUID PRIMARY KEY,
    holder_name   VARCHAR(255) NOT NULL,
    balance       NUMERIC(19, 2) NOT NULL CHECK (balance >= 0)
);
