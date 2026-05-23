package com.example.flexhub.events;

import java.math.BigDecimal;
import java.util.UUID;

// Terminal-success event of the saga happy path. Consumed by transaction-service to mark
// the Transaction COMPLETED.
public record AccountCredited(
        UUID eventId,
        UUID transferId,
        UUID destinationAccountId,
        BigDecimal amount) {
}
