package com.example.flexhub.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_events", schema = "accounts")
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
