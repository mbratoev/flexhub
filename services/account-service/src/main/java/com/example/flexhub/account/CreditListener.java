package com.example.flexhub.account;

import com.example.flexhub.events.AccountCredited;
import com.example.flexhub.events.AccountDebited;
import com.example.flexhub.events.CreditFailed;
import com.example.flexhub.events.Topics;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Saga step 2 — credit the destination account. Self-consumes the AccountDebited event
// produced by step 1; this is intentional choreography (no orchestrator), letting the
// two steps run in their own DB transactions with independent consumer offsets.
//
// On failure (destination missing or flagged rejects-credits), we emit CreditFailed
// rather than throw — that's a deliberate business outcome, not a transient error,
// so the saga walks to compensation rather than triggering Kafka-level retry.
@Component
class CreditListener {

    private static final Logger log = LoggerFactory.getLogger(CreditListener.class);

    private final AccountService accountService;
    private final ProcessedEventRepository processedEvents;
    private final OutboxPublisher outbox;

    CreditListener(AccountService accountService,
                   ProcessedEventRepository processedEvents,
                   OutboxPublisher outbox) {
        this.accountService = accountService;
        this.processedEvents = processedEvents;
        this.outbox = outbox;
    }

    @KafkaListener(topics = Topics.ACCOUNTS_DEBITED, groupId = "account-service.credit",
            properties = "spring.json.value.default.type=com.example.flexhub.events.AccountDebited")
    @Transactional
    void onAccountDebited(AccountDebited event) {
        if (processedEvents.recordIfFirstSeen(event.eventId(), Topics.ACCOUNTS_DEBITED) == 0) {
            log.info("Skipping redelivered event {} (already processed)", event.eventId());
            return;
        }

        log.info("Saga step 2 (credit): {}", event);
        UUID emitId = UUID.randomUUID();
        String key = event.transferId().toString();

        switch (accountService.credit(event.destinationAccountId(), event.amount())) {
            case AccountService.CreditResult.Success s ->
                    outbox.publish(emitId, Topics.ACCOUNTS_CREDITED, key,
                            new AccountCredited(emitId, event.transferId(),
                                    event.destinationAccountId(), event.amount()));
            case AccountService.CreditResult.AccountNotFound a ->
                    outbox.publish(emitId, Topics.ACCOUNTS_CREDIT_FAILED, key,
                            new CreditFailed(emitId, event.transferId(),
                                    event.sourceAccountId(), event.amount(),
                                    CreditFailed.Reason.DESTINATION_ACCOUNT_NOT_FOUND,
                                    "destination account " + event.destinationAccountId() + " not found"));
            case AccountService.CreditResult.RejectsCredits r ->
                    outbox.publish(emitId, Topics.ACCOUNTS_CREDIT_FAILED, key,
                            new CreditFailed(emitId, event.transferId(),
                                    event.sourceAccountId(), event.amount(),
                                    CreditFailed.Reason.DESTINATION_REJECTS_CREDITS,
                                    "destination account " + event.destinationAccountId() + " is flagged to reject credits"));
        }
    }
}
