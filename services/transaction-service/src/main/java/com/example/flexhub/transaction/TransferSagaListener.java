package com.example.flexhub.transaction;

import com.example.flexhub.events.AccountCredited;
import com.example.flexhub.events.AccountDebited;
import com.example.flexhub.events.CreditFailed;
import com.example.flexhub.events.DebitRejected;
import com.example.flexhub.events.Topics;
import com.example.flexhub.events.TransferReversed;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// transaction-service is the saga's state holder. It doesn't perform any business work
// itself — it just walks the Transaction state machine in response to events emitted by
// account-service. Each handler:
//   1. Idempotent-consumer dedup via processed_events
//   2. Look up the Transaction by transferId
//   3. Apply the state transition (Hibernate dirty-checking flushes at commit)
//
// State machine:
//   PENDING --AccountDebited--> DEBITED --AccountCredited--> COMPLETED      (happy path)
//   PENDING --DebitRejected--> FAILED                                       (no compensation needed)
//   DEBITED --CreditFailed--> REVERSED --TransferReversed--> FAILED          (compensation path)
@Component
class TransferSagaListener {

    private static final Logger log = LoggerFactory.getLogger(TransferSagaListener.class);

    private final TransactionRepository repository;
    private final ProcessedEventRepository processedEvents;

    TransferSagaListener(TransactionRepository repository, ProcessedEventRepository processedEvents) {
        this.repository = repository;
        this.processedEvents = processedEvents;
    }

    @KafkaListener(topics = Topics.ACCOUNTS_DEBITED, groupId = "transaction-service.debited",
            properties = "spring.json.value.default.type=com.example.flexhub.events.AccountDebited")
    @Transactional
    void onAccountDebited(AccountDebited event) {
        if (isDuplicate(event.eventId(), Topics.ACCOUNTS_DEBITED)) return;
        applyTo(event.transferId(), Transaction::markDebited, "DEBITED");
    }

    @KafkaListener(topics = Topics.ACCOUNTS_DEBIT_REJECTED, groupId = "transaction-service.debit-rejected",
            properties = "spring.json.value.default.type=com.example.flexhub.events.DebitRejected")
    @Transactional
    void onDebitRejected(DebitRejected event) {
        if (isDuplicate(event.eventId(), Topics.ACCOUNTS_DEBIT_REJECTED)) return;
        String reason = event.reason().name() + ": " + event.detail();
        applyTo(event.transferId(), t -> t.markFailed(reason), "FAILED (debit rejected: " + reason + ")");
    }

    @KafkaListener(topics = Topics.ACCOUNTS_CREDITED, groupId = "transaction-service.credited",
            properties = "spring.json.value.default.type=com.example.flexhub.events.AccountCredited")
    @Transactional
    void onAccountCredited(AccountCredited event) {
        if (isDuplicate(event.eventId(), Topics.ACCOUNTS_CREDITED)) return;
        applyTo(event.transferId(), Transaction::markCompleted, "COMPLETED");
    }

    @KafkaListener(topics = Topics.ACCOUNTS_CREDIT_FAILED, groupId = "transaction-service.credit-failed",
            properties = "spring.json.value.default.type=com.example.flexhub.events.CreditFailed")
    @Transactional
    void onCreditFailed(CreditFailed event) {
        if (isDuplicate(event.eventId(), Topics.ACCOUNTS_CREDIT_FAILED)) return;
        String reason = event.reason().name() + ": " + event.detail();
        applyTo(event.transferId(), t -> t.markReversed(reason), "REVERSED (awaiting refund: " + reason + ")");
    }

    @KafkaListener(topics = Topics.TRANSFERS_REVERSED, groupId = "transaction-service.reversed",
            properties = "spring.json.value.default.type=com.example.flexhub.events.TransferReversed")
    @Transactional
    void onTransferReversed(TransferReversed event) {
        if (isDuplicate(event.eventId(), Topics.TRANSFERS_REVERSED)) return;
        applyTo(event.transferId(), t -> t.markFailed("REVERSED: " + event.originalFailureReason()),
                "FAILED (compensation complete)");
    }

    private boolean isDuplicate(UUID eventId, String topic) {
        if (processedEvents.recordIfFirstSeen(eventId, topic) == 0) {
            log.info("Skipping redelivered event {} from {} (already processed)", eventId, topic);
            return true;
        }
        return false;
    }

    private void applyTo(UUID transferId, java.util.function.Consumer<Transaction> mutation, String description) {
        Optional<Transaction> tx = repository.findById(transferId);
        if (tx.isEmpty()) {
            log.warn("Saga event for unknown transferId={}", transferId);
            return;
        }
        mutation.accept(tx.get());
        log.info("Transaction {} -> {}", transferId, description);
    }
}
