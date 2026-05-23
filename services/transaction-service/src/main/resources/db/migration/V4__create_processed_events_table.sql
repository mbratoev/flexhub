-- Phase 3 — Stage C: consumer-side dedup.
-- TransferCompletionListener inserts the event_id BEFORE applying the business work.
-- On duplicate (redelivery from Kafka, consumer rebalance, etc.) the INSERT is a no-op
-- via ON CONFLICT DO NOTHING, and the listener exits early without re-applying the work.

CREATE TABLE transactions.processed_events (
    event_id      UUID PRIMARY KEY,
    topic         VARCHAR(255) NOT NULL,
    processed_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
