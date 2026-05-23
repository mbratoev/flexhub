package com.example.flexhub.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

// JPA needs a no-arg constructor and field/method access — so this can't be a Java record.
// Keep it small and intentional: only the fields that matter, no service logic.
@Entity
@Table(name = "accounts", schema = "accounts")
public class Account {

    @Id
    private UUID id;

    @Column(name = "holder_name", nullable = false)
    private String holderName;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    // If true, any attempt to credit this account fails with
    // CreditFailed{DESTINATION_REJECTS_CREDITS}. Lets the demo deterministically
    // trigger the saga compensation path. A real bank would use a richer
    // account_status enum (ACTIVE / FROZEN / CLOSED / RESTRICTED / ...).
    @Column(name = "rejects_credits", nullable = false)
    private boolean rejectsCredits;

    protected Account() {
        // JPA only
    }

    public Account(UUID id, String holderName, BigDecimal balance) {
        this(id, holderName, balance, false);
    }

    public Account(UUID id, String holderName, BigDecimal balance, boolean rejectsCredits) {
        this.id = id;
        this.holderName = holderName;
        this.balance = balance;
        this.rejectsCredits = rejectsCredits;
    }

    public UUID getId() { return id; }
    public String getHolderName() { return holderName; }
    public BigDecimal getBalance() { return balance; }
    public boolean isRejectsCredits() { return rejectsCredits; }

    // Balance mutations are kept inside the entity so all changes go through one place.
    void debit(BigDecimal amount) { this.balance = this.balance.subtract(amount); }
    void credit(BigDecimal amount) { this.balance = this.balance.add(amount); }
}
