package com.example.flexhub.account;

import com.example.flexhub.events.AccountCredited;
import com.example.flexhub.events.AccountDebited;
import com.example.flexhub.events.Topics;
import com.example.flexhub.events.TransferReversed;
import java.math.BigDecimal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// CQRS read-side: builds the account_statement projection by consuming saga events.
//   AccountDebited       → entry on source with delta = -amount
//   AccountCredited      → entry on destination with delta = +amount
//   TransferReversed     → entry on source with delta = +amount (the refund)
//
// Note this listener writes ONLY to the read model (account_statement). It NEVER touches
// the write tables (accounts, transactions). That separation is what makes this CQRS:
// commands on the write side, queries on a denormalized read side, kept eventually
// consistent via events on Kafka.
@Component
class StatementProjectionListener {

    private static final Logger log = LoggerFactory.getLogger(StatementProjectionListener.class);

    private final StatementEntryRepository statement;
    private final StatementDedupRepository dedup;

    StatementProjectionListener(StatementEntryRepository statement, StatementDedupRepository dedup) {
        this.statement = statement;
        this.dedup = dedup;
    }

    @KafkaListener(topics = Topics.ACCOUNTS_DEBITED, groupId = "account-service.statement-projection.debited",
            properties = "spring.json.value.default.type=com.example.flexhub.events.AccountDebited")
    @Transactional
    void onAccountDebited(AccountDebited event) {
        if (isDuplicate(event.eventId(), Topics.ACCOUNTS_DEBITED)) return;
        record(event.sourceAccountId(), event.transferId(), event.amount().negate(), "DEBIT");
    }

    @KafkaListener(topics = Topics.ACCOUNTS_CREDITED, groupId = "account-service.statement-projection.credited",
            properties = "spring.json.value.default.type=com.example.flexhub.events.AccountCredited")
    @Transactional
    void onAccountCredited(AccountCredited event) {
        if (isDuplicate(event.eventId(), Topics.ACCOUNTS_CREDITED)) return;
        record(event.destinationAccountId(), event.transferId(), event.amount(), "CREDIT");
    }

    @KafkaListener(topics = Topics.TRANSFERS_REVERSED, groupId = "account-service.statement-projection.reversed",
            properties = "spring.json.value.default.type=com.example.flexhub.events.TransferReversed")
    @Transactional
    void onTransferReversed(TransferReversed event) {
        if (isDuplicate(event.eventId(), Topics.TRANSFERS_REVERSED)) return;
        record(event.sourceAccountId(), event.transferId(), event.amount(), "REFUND");
    }

    private boolean isDuplicate(UUID eventId, String topic) {
        if (dedup.recordIfFirstSeen(eventId, topic) == 0) {
            log.info("[statement] skipping redelivered event {} from {}", eventId, topic);
            return true;
        }
        return false;
    }

    private void record(UUID accountId, UUID transferId, BigDecimal delta, String reason) {
        statement.save(new StatementEntry(UUID.randomUUID(), accountId, transferId, delta, reason));
        log.info("[statement] account={} transfer={} delta={} reason={}", accountId, transferId, delta, reason);
    }
}
