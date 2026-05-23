package com.example.flexhub.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

// Dedicated dedup table for the StatementProjectionListener. Sibling of processed_events
// but isolated to avoid collisions with other consumers (CreditListener, RefundListener)
// that consume the same events for write-side work.
@Entity
@Table(name = "statement_dedup", schema = "accounts")
public class StatementDedup {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(nullable = false)
    private String topic;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    protected StatementDedup() {}

    public StatementDedup(UUID eventId, String topic) {
        this.eventId = eventId;
        this.topic = topic;
        this.processedAt = Instant.now();
    }

    public UUID getEventId() { return eventId; }
    public String getTopic() { return topic; }
    public Instant getProcessedAt() { return processedAt; }
}
