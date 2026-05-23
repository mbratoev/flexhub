-- Phase 3 — Stage C: consumer-side dedup.
-- TransferRequestListener inserts the event_id BEFORE applying the debit/credit.
-- On duplicate (Kafka redelivery, consumer rebalance) the INSERT returns 0 rows
-- and the listener exits early — no double-debit.

CREATE TABLE accounts.processed_events (
    event_id      UUID PRIMARY KEY,
    topic         VARCHAR(255) NOT NULL,
    processed_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
