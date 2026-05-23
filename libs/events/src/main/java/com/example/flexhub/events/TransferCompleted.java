package com.example.flexhub.events;

import java.util.UUID;

// Published by account-service to topic `transfers.completed` after applying (or rejecting) a debit/credit.
// Consumed by transaction-service to flip the Transaction state machine to COMPLETED or FAILED.
//
// eventId is unique per emission and corresponds to account-service's outbox_event row.
public record TransferCompleted(
        UUID eventId,
        UUID transferId,
        Status status,
        String reason) {

    public enum Status {
        SUCCESS,
        INSUFFICIENT_FUNDS,
        ACCOUNT_NOT_FOUND
    }

    public static TransferCompleted success(UUID eventId, UUID transferId) {
        return new TransferCompleted(eventId, transferId, Status.SUCCESS, null);
    }

    public static TransferCompleted insufficientFunds(UUID eventId, UUID transferId, String reason) {
        return new TransferCompleted(eventId, transferId, Status.INSUFFICIENT_FUNDS, reason);
    }

    public static TransferCompleted accountNotFound(UUID eventId, UUID transferId, String reason) {
        return new TransferCompleted(eventId, transferId, Status.ACCOUNT_NOT_FOUND, reason);
    }
}
