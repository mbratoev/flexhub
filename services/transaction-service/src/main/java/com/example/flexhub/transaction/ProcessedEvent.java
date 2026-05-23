package com.example.flexhub.transaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

// One row per event_id this service has processed from Kafka. Acts as the dedup table
// for the idempotent-consumer pattern. Re-delivered events are recognized by eventId.
@Entity
@Table(name = "processed_events", schema = "transactions")
public class ProcessedEvent {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(nullable = false)
    private String topic;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    protected ProcessedEvent() {}

    public ProcessedEvent(UUID eventId, String topic) {
        this.eventId = eventId;
        this.topic = topic;
        this.processedAt = Instant.now();
    }

    public UUID getEventId() { return eventId; }
    public String getTopic() { return topic; }
    public Instant getProcessedAt() { return processedAt; }
}
