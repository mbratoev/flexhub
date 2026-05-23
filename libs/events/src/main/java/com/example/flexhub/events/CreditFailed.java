package com.example.flexhub.events;

import java.math.BigDecimal;
import java.util.UUID;

// Emitted by account-service when the credit step cannot succeed (destination missing,
// destination flagged "reject credits", etc.). Two consumers:
//   - account-service (self), to perform the compensating refund of the source
//   - transaction-service, to advance Transaction to REVERSED (intermediate)
//
// The CreditFailed reason is permanent (Option A from failure-handling-and-retry-strategies.md):
// no auto-retry; immediate compensation.
public record CreditFailed(
        UUID eventId,
        UUID transferId,
        UUID sourceAccountId,
        BigDecimal amount,
        Reason reason,
        String detail) {

    public enum Reason {
        DESTINATION_ACCOUNT_NOT_FOUND,
        DESTINATION_REJECTS_CREDITS
    }
}
