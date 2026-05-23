package com.example.flexhub.events;

import java.math.BigDecimal;
import java.util.UUID;

// Final compensation event. Emitted by account-service after re-crediting the source
// (undoing the original debit). Consumed by transaction-service to mark the Transaction
// FAILED with a reverse-completion reason. Terminal for the compensation path.
public record TransferReversed(
        UUID eventId,
        UUID transferId,
        UUID sourceAccountId,
        BigDecimal amount,
        String originalFailureReason) {
}
