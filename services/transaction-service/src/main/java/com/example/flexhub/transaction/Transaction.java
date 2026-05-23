package com.example.flexhub.transaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions", schema = "transactions")
public class Transaction {

    public enum State {
        PENDING,        // accepted by REST, TransferRequested published
        DEBITED,        // saga: source debited, credit step in flight
        COMPLETED,      // saga happy-path terminal — both ledger sides applied
        REVERSED,       // saga: credit failed, compensation in flight (source being refunded)
        FAILED          // terminal failure — either debit rejected (no compensation needed)
                        // or compensation completed (source already refunded)
    }

    @Id
    private UUID id;

    @Column(name = "source_account_id", nullable = false)
    private UUID sourceAccountId;

    @Column(name = "destination_account_id", nullable = false)
    private UUID destinationAccountId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private State state;

    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Transaction() {}

    public Transaction(UUID id, UUID sourceAccountId, UUID destinationAccountId, BigDecimal amount) {
        this.id = id;
        this.sourceAccountId = sourceAccountId;
        this.destinationAccountId = destinationAccountId;
        this.amount = amount;
        this.state = State.PENDING;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public void markDebited() {
        this.state = State.DEBITED;
    }

    public void markCompleted() {
        this.state = State.COMPLETED;
        this.reason = null;
    }

    public void markReversed(String reason) {
        this.state = State.REVERSED;
        this.reason = reason;
    }

    public void markFailed(String reason) {
        this.state = State.FAILED;
        this.reason = reason;
    }

    public UUID getId() { return id; }
    public UUID getSourceAccountId() { return sourceAccountId; }
    public UUID getDestinationAccountId() { return destinationAccountId; }
    public BigDecimal getAmount() { return amount; }
    public State getState() { return state; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
