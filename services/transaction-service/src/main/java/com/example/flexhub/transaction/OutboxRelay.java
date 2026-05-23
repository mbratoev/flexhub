package com.example.flexhub.transaction;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Polls the outbox table and forwards unsent events to Kafka.
//
// Why this is safe:
//   1. The whole method is @Transactional. The SELECT ... FOR UPDATE SKIP LOCKED takes
//      row-level locks. If the JVM crashes before kafka.send() returns, the transaction
//      rolls back, sent_at stays NULL, and the next tick reprocesses the row.
//   2. If kafka.send() succeeds but the transaction fails to commit (rare), the same row
//      will be published again on the next tick. Consumers MUST be idempotent — that's
//      what Stage C wires up. The pattern is at-least-once, not exactly-once.
//   3. SKIP LOCKED lets multiple transaction-service replicas run this loop concurrently
//      without double-publishing rows.
@Component
class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH_SIZE = 50;

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> stringKafkaTemplate;

    OutboxRelay(OutboxEventRepository repository, KafkaTemplate<String, String> stringKafkaTemplate) {
        this.repository = repository;
        this.stringKafkaTemplate = stringKafkaTemplate;
    }

    @Scheduled(
            fixedDelayString = "${app.outbox.poll-delay-ms:100}",
            initialDelayString = "${app.outbox.initial-delay-ms:1000}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> batch = repository.lockBatchForPublish(BATCH_SIZE);
        if (batch.isEmpty()) return;

        for (OutboxEvent event : batch) {
            try {
                // .get() blocks until the broker acks. A failure throws, the surrounding
                // @Transactional rolls back, sent_at stays NULL, and the next tick retries.
                // For higher throughput you'd batch the sends and flush() at the end.
                stringKafkaTemplate.send(event.getTopic(), event.getAggregateId(), event.getPayload()).get();
                event.markSent();
                log.info("Outbox -> Kafka: id={} topic={} key={}",
                        event.getId(), event.getTopic(), event.getAggregateId());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Outbox relay interrupted while publishing " + event.getId(), e);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to publish outbox event " + event.getId(), e);
            }
        }
    }
}
