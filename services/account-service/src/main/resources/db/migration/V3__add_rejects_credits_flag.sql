-- Phase 4 — Stage A: lets us seed a "failing destination" account that always rejects credits.
-- Used to demonstrate the saga compensation path end-to-end.
-- In a real bank this would be `account_status` enum with values ACTIVE / FROZEN / CLOSED / etc.

ALTER TABLE accounts.accounts
    ADD COLUMN rejects_credits BOOLEAN NOT NULL DEFAULT FALSE;
