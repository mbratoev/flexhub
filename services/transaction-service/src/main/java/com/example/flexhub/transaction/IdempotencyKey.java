package com.example.flexhub.transaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

// Maps an opaque client-supplied key (e.g. "stripe-style-uuid") to the UUID of the
// transaction created on the original request. Retries with the same key return the
// original transaction's current state — no duplicate transfers.
@Entity
@Table(name = "idempotency_keys", schema = "transactions")
public class IdempotencyKey {

    @Id
    @Column(name = "idempotency_key", length = 255)
    private String idempotencyKey;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IdempotencyKey() {}

    public IdempotencyKey(String idempotencyKey, UUID transactionId) {
        this.idempotencyKey = idempotencyKey;
        this.transactionId = transactionId;
        this.createdAt = Instant.now();
    }

    public String getIdempotencyKey() { return idempotencyKey; }
    public UUID getTransactionId() { return transactionId; }
    public Instant getCreatedAt() { return createdAt; }
}
