package com.example.flexhub.account;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Same relay pattern as transaction-service's OutboxRelay. Polls unsent rows, locks them
// with FOR UPDATE SKIP LOCKED, publishes synchronously (.send().get()), marks sent at
// transaction commit via JPA dirty-checking.
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
