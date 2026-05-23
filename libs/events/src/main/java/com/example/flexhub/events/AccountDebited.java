package com.example.flexhub.events;

import java.math.BigDecimal;
import java.util.UUID;

// Saga step 2 — emitted by account-service after a successful debit. Two consumers:
//   - account-service (self), to drive the credit step
//   - transaction-service, to advance the Transaction state machine to DEBITED
public record AccountDebited(
        UUID eventId,
        UUID transferId,
        UUID sourceAccountId,
        UUID destinationAccountId,
        BigDecimal amount) {
}
