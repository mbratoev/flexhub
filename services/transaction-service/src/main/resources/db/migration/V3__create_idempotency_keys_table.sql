-- Phase 3 — Stage C: REST-side idempotency.
-- Client may send `Idempotency-Key: <opaque-string>` on POST /transfers. If a key has been
-- seen before, we return the response for the original transaction instead of creating a
-- new one. This is the standard pattern from Stripe/AWS APIs.

CREATE TABLE transactions.idempotency_keys (
    idempotency_key  VARCHAR(255) PRIMARY KEY,
    transaction_id   UUID NOT NULL REFERENCES transactions.transactions(id),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
