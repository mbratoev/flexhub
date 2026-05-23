-- Phase 4 — Stage B/C: transactional outbox in account-service.
-- Closes the dual-write problem on the account-service side: every emission of
-- AccountDebited, AccountCredited, CreditFailed, TransferReversed (etc.) is written
-- in the same DB transaction as the balance mutation that produced it.

CREATE TABLE accounts.outbox_event (
    id              UUID PRIMARY KEY,
    aggregate_id    VARCHAR(255) NOT NULL,   -- used as the Kafka message key (preserves ordering per aggregate)
    topic           VARCHAR(255) NOT NULL,
    payload         TEXT NOT NULL,           -- pre-serialized JSON of the event
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    sent_at         TIMESTAMP WITH TIME ZONE                            -- NULL = still pending
);

CREATE INDEX idx_outbox_unsent ON accounts.outbox_event (created_at) WHERE sent_at IS NULL;
