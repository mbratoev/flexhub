package com.example.flexhub.events;

import java.math.BigDecimal;
import java.util.UUID;

// Published by transaction-service to topic `transfers.requested` when a client calls POST /transfers.
// Consumed by account-service, which debits source + credits destination, then publishes a
// TransferCompleted with the outcome.
//
// eventId is unique per emission of this event (corresponds to the outbox_event row id).
// Same business event redelivered by Kafka always carries the same eventId — that's the
// dedup key for the idempotent-consumer pattern. transferId identifies the business object
// (the transfer); eventId identifies the message.
public record TransferRequested(
        UUID eventId,
        UUID transferId,
        UUID sourceAccountId,
        UUID destinationAccountId,
        BigDecimal amount) {
}
