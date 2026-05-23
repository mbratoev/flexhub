-- Phase 5: notification-service tables.
-- notifications: one row per "would-have-been-sent" message; the body field is the rendered text
-- the user would receive. The actual send is mocked (a log line) since wiring email/SMS isn't the
-- learning goal.
-- processed_events: idempotent-consumer dedup, same shape as other services.

CREATE TABLE notifications.notifications (
    id            UUID PRIMARY KEY,
    transfer_id   UUID NOT NULL,
    type          VARCHAR(32) NOT NULL,           -- TRANSFER_COMPLETED | TRANSFER_FAILED | TRANSFER_REVERSED
    body          TEXT NOT NULL,
    sent_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notifications_transfer_id ON notifications.notifications(transfer_id);

CREATE TABLE notifications.processed_events (
    event_id      UUID PRIMARY KEY,
    topic         VARCHAR(255) NOT NULL,
    processed_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
