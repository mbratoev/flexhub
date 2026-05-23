-- transaction-service Phase 3 — Stage A: persistence baseline.
-- The transactions schema is created by infra/postgres/init.sql before Flyway runs.

CREATE TABLE transactions.transactions (
    id                       UUID PRIMARY KEY,
    source_account_id        UUID NOT NULL,
    destination_account_id   UUID NOT NULL,
    amount                   NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
    state                    VARCHAR(32) NOT NULL,
    reason                   TEXT,
    created_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_transactions_state ON transactions.transactions(state);
