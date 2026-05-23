package com.example.flexhub.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// CQRS read-side projection row. Built by StatementProjectionListener from saga events.
// One row per balance-change moment (debit, credit, refund). delta is signed — positive
// means money flowed INTO the account, negative means OUT.
@Entity
@Table(name = "account_statement", schema = "accounts")
public class StatementEntry {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "transfer_id", nullable = false)
    private UUID transferId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal delta;

    @Column(nullable = false, length = 64)
    private String reason;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected StatementEntry() {}

    public StatementEntry(UUID id, UUID accountId, UUID transferId, BigDecimal delta, String reason) {
        this.id = id;
        this.accountId = accountId;
        this.transferId = transferId;
        this.delta = delta;
        this.reason = reason;
        this.occurredAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getAccountId() { return accountId; }
    public UUID getTransferId() { return transferId; }
    public BigDecimal getDelta() { return delta; }
    public String getReason() { return reason; }
    public Instant getOccurredAt() { return occurredAt; }
}
