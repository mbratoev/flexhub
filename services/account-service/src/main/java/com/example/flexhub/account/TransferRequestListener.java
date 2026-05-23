package com.example.flexhub.account;

import com.example.flexhub.events.AccountDebited;
import com.example.flexhub.events.DebitRejected;
import com.example.flexhub.events.Topics;
import com.example.flexhub.events.TransferRequested;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Saga step 1 — debit the source account. The credit step lives in CreditListener
// (consumes AccountDebited); compensation lives in RefundListener (consumes CreditFailed).
// Each step has its own consumer group, offset, and retry policy.
@Component
class TransferRequestListener {

    private static final Logger log = LoggerFactory.getLogger(TransferRequestListener.class);

    private final AccountService accountService;
    private final ProcessedEventRepository processedEvents;
    private final OutboxPublisher outbox;

    TransferRequestListener(AccountService accountService,
                            ProcessedEventRepository processedEvents,
                            OutboxPublisher outbox) {
        this.accountService = accountService;
        this.processedEvents = processedEvents;
        this.outbox = outbox;
    }

    @KafkaListener(topics = Topics.TRANSFERS_REQUESTED, groupId = "account-service.debit",
            properties = "spring.json.value.default.type=com.example.flexhub.events.TransferRequested")
    @Transactional
    void onTransferRequested(TransferRequested event) {
        if (processedEvents.recordIfFirstSeen(event.eventId(), Topics.TRANSFERS_REQUESTED) == 0) {
            log.info("Skipping redelivered event {} (already processed)", event.eventId());
            return;
        }

        log.info("Saga step 1 (debit): {}", event);
        UUID emitId = UUID.randomUUID();
        String key = event.transferId().toString();

        switch (accountService.debit(event.sourceAccountId(), event.amount())) {
            case AccountService.DebitResult.Success s ->
                    outbox.publish(emitId, Topics.ACCOUNTS_DEBITED, key,
                            new AccountDebited(emitId, event.transferId(), event.sourceAccountId(),
                                    event.destinationAccountId(), event.amount()));
            case AccountService.DebitResult.InsufficientFunds f ->
                    outbox.publish(emitId, Topics.ACCOUNTS_DEBIT_REJECTED, key,
                            new DebitRejected(emitId, event.transferId(),
                                    DebitRejected.Reason.INSUFFICIENT_FUNDS,
                                    "balance " + f.currentBalance() + " below requested amount " + event.amount()));
            case AccountService.DebitResult.AccountNotFound a ->
                    outbox.publish(emitId, Topics.ACCOUNTS_DEBIT_REJECTED, key,
                            new DebitRejected(emitId, event.transferId(),
                                    DebitRejected.Reason.SOURCE_ACCOUNT_NOT_FOUND,
                                    "source account " + event.sourceAccountId() + " not found"));
        }
    }
}
