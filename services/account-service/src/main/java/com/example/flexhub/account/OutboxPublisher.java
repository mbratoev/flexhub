package com.example.flexhub.account;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// Helper that serializes an event to JSON and writes the outbox row. Used by all three
// account-service saga listeners. Called inside an existing @Transactional method —
// the outbox row commits atomically with the business state change.
@Component
class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    OutboxPublisher(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    void publish(UUID eventId, String topic, String aggregateId, Object event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            repository.save(new OutboxEvent(eventId, topic, aggregateId, payload));
            log.info("Outbox queued: topic={} eventId={}", topic, eventId);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize event of type " + event.getClass().getName(), e);
        }
    }
}
