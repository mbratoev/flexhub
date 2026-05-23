package com.example.flexhub.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications", schema = "notifications")
public class Notification {

    public enum Type {
        TRANSFER_COMPLETED,
        TRANSFER_FAILED,
        TRANSFER_REVERSED
    }

    @Id
    private UUID id;

    @Column(name = "transfer_id", nullable = false)
    private UUID transferId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Type type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    protected Notification() {}

    public Notification(UUID id, UUID transferId, Type type, String body) {
        this.id = id;
        this.transferId = transferId;
        this.type = type;
        this.body = body;
        this.sentAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTransferId() { return transferId; }
    public Type getType() { return type; }
    public String getBody() { return body; }
    public Instant getSentAt() { return sentAt; }
}
