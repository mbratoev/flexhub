-- Transactional Outbox pattern (Phase 3 — Stage B).
--
-- Every Kafka publish goes through this table. TransferController writes to
-- transactions.transactions AND to transactions.outbox_event in the SAME DB transaction.
-- A separate @Scheduled relay polls unsent rows, publishes them to Kafka, and marks them sent.
--
-- Why: it eliminates the dual-write problem. Without an outbox, a crash between
-- "INSERT transaction" and "kafkaTemplate.send(...)" leaves the DB and Kafka inconsistent.

CREATE TABLE transactions.outbox_event (
    id              UUID PRIMARY KEY,
    aggregate_id    VARCHAR(255) NOT NULL,   -- used as the Kafka message key (preserves per-aggregate ordering)
    topic           VARCHAR(255) NOT NULL,
    payload         TEXT NOT NULL,           -- pre-serialized JSON of the event
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    sent_at         TIMESTAMP WITH TIME ZONE                            -- NULL = still pending
);

-- Partial index for the relay's hot-path query: only scan unsent rows, in insertion order.
CREATE INDEX idx_outbox_unsent ON transactions.outbox_event (created_at) WHERE sent_at IS NULL;
