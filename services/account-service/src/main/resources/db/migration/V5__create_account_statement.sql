-- Phase 5: CQRS read-side projection.
-- account_statement is a denormalized read model built by consuming saga events.
-- It lives in the SAME schema as the write model (`accounts.accounts`) but is logically
-- separate: write-side commands NEVER touch this table, and read-side queries
-- (GET /accounts/{id}/statement) NEVER touch the write tables.
--
-- Each row is one ledger entry — a positive amount means money entered the account,
-- a negative means money left. The transferId provides traceability back to the saga.

CREATE TABLE accounts.account_statement (
    id              UUID PRIMARY KEY,
    account_id      UUID NOT NULL,
    transfer_id     UUID NOT NULL,
    delta           NUMERIC(19, 2) NOT NULL,
    reason          VARCHAR(64) NOT NULL,
    occurred_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Hot-path query: statement for a specific account in chronological order.
CREATE INDEX idx_statement_account_time ON accounts.account_statement (account_id, occurred_at DESC);

-- Dedup table dedicated to the projection consumer.
-- Why a separate table from accounts.processed_events: the existing dedup table is shared
-- across CreditListener, RefundListener, etc. The projection consumes accounts.debited
-- which CreditListener also consumes — different consumer groups, same event_id. With one
-- shared dedup table keyed only by event_id, the second consumer would see "already
-- processed" and skip. The architecturally cleaner fix is a composite key on
-- (consumer_group, event_id); we keep that as a deferred refactor and isolate the
-- projection's dedup here.
CREATE TABLE accounts.statement_dedup (
    event_id      UUID PRIMARY KEY,
    topic         VARCHAR(255) NOT NULL,
    processed_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
