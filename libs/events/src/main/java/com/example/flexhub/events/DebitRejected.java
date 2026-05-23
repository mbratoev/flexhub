package com.example.flexhub.events;

import java.util.UUID;

// Emitted by account-service when the debit step cannot proceed (insufficient funds, source
// account missing). Terminal for the saga — no compensation needed (no debit happened).
// Consumed by transaction-service to mark Transaction as FAILED.
public record DebitRejected(
        UUID eventId,
        UUID transferId,
        Reason reason,
        String detail) {

    public enum Reason {
        INSUFFICIENT_FUNDS,
        SOURCE_ACCOUNT_NOT_FOUND
    }
}
