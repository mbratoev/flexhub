package com.example.flexhub.events;

// Single source of truth for Kafka topic names.
public final class Topics {

    // Command from transaction-service to start a transfer.
    public static final String TRANSFERS_REQUESTED = "transfers.requested";

    // Legacy aggregate event — kept for backwards-compat with any historical readers.
    // The saga uses the more granular events below instead.
    public static final String TRANSFERS_COMPLETED = "transfers.completed";

    // Saga events. All emitted by account-service and consumed by transaction-service;
    // ACCOUNTS_DEBITED and ACCOUNTS_CREDIT_FAILED are also self-consumed by account-service
    // for the next saga step.
    public static final String ACCOUNTS_DEBITED       = "accounts.debited";
    public static final String ACCOUNTS_DEBIT_REJECTED = "accounts.debit-rejected";
    public static final String ACCOUNTS_CREDITED      = "accounts.credited";
    public static final String ACCOUNTS_CREDIT_FAILED = "accounts.credit-failed";
    public static final String TRANSFERS_REVERSED    = "transfers.reversed";

    private Topics() {
    }
}
