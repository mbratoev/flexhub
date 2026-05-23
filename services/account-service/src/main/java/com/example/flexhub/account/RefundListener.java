package com.example.flexhub.account;

import com.example.flexhub.events.CreditFailed;
import com.example.flexhub.events.Topics;
import com.example.flexhub.events.TransferReversed;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Saga compensation step — fires when CreditFailed indicates the saga can't complete.
// Re-credits the source (undoing the original debit) and emits TransferReversed so
// transaction-service can mark the Transaction FAILED. This is the choreography form
// of compensation: no orchestrator decides "now reverse" — account-service decides
// for itself based on the CreditFailed event.
//
// We use creditUnconditional on the source because the source was already valid enough
// to debit from in step 1; bypassing the rejects-credits check here is safe.
@Component
class RefundListener {

    private static final Logger log = LoggerFactory.getLogger(RefundListener.class);

    private final AccountService accountService;
    private final ProcessedEventRepository processedEvents;
    private final OutboxPublisher outbox;

    RefundListener(AccountService accountService,
                   ProcessedEventRepository processedEvents,
                   OutboxPublisher outbox) {
        this.accountService = accountService;
        this.processedEvents = processedEvents;
        this.outbox = outbox;
    }

    @KafkaListener(topics = Topics.ACCOUNTS_CREDIT_FAILED, groupId = "account-service.refund",
            properties = "spring.json.value.default.type=com.example.flexhub.events.CreditFailed")
    @Transactional
    void onCreditFailed(CreditFailed event) {
        if (processedEvents.recordIfFirstSeen(event.eventId(), Topics.ACCOUNTS_CREDIT_FAILED) == 0) {
            log.info("Skipping redelivered event {} (already processed)", event.eventId());
            return;
        }

        log.info("Saga compensation (refund source): {}", event);

        var refund = accountService.creditUnconditional(event.sourceAccountId(), event.amount());
        if (refund.isEmpty()) {
            // The source disappeared between debit and refund. Genuinely shouldn't happen
            // in a normal banking flow; log loudly and let an alert fire. We do NOT throw —
            // re-running the refund won't help if the account is genuinely gone. In a real
            // system this becomes an ops ticket on a "stuck transactions" dashboard.
            log.error("Cannot refund source account {} for transfer {}: account missing",
                    event.sourceAccountId(), event.transferId());
            return;
        }

        UUID emitId = UUID.randomUUID();
        String key = event.transferId().toString();
        outbox.publish(emitId, Topics.TRANSFERS_REVERSED, key,
                new TransferReversed(emitId, event.transferId(),
                        event.sourceAccountId(), event.amount(),
                        event.reason().name() + ": " + event.detail()));
    }
}
