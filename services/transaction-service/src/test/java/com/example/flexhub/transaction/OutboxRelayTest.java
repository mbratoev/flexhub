package com.example.flexhub.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

// Pure unit test — no Spring context, no broker, no DB. Mocks the repository and template,
// drives publishPendingEvents() directly, and asserts on Kafka calls + entity state.
//
// What this DOESN'T cover:
//   - The @Scheduled timer actually firing (covered by manual smoke test today; will be
//     covered by a future EmbeddedKafka integration test).
//   - SELECT ... FOR UPDATE SKIP LOCKED behaviour on real Postgres under concurrent load
//     (covered by the Testcontainers Kafka+Postgres end-to-end test).
class OutboxRelayTest {

    private OutboxEventRepository repository;
    private KafkaTemplate<String, String> kafkaTemplate;
    private OutboxRelay relay;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(OutboxEventRepository.class);
        kafkaTemplate = mock(KafkaTemplate.class);
        relay = new OutboxRelay(repository, kafkaTemplate);
    }

    @Test
    void emptyBatchIsANoop() {
        when(repository.lockBatchForPublish(anyInt())).thenReturn(List.of());

        relay.publishPendingEvents();

        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void publishesEachUnsentRowAndMarksSent() {
        OutboxEvent e1 = newOutbox("transfers.requested", "key-1", "{\"a\":1}");
        OutboxEvent e2 = newOutbox("transfers.requested", "key-2", "{\"b\":2}");
        when(repository.lockBatchForPublish(anyInt())).thenReturn(List.of(e1, e2));
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(completedSend());

        assertThat(e1.getSentAt()).isNull();
        assertThat(e2.getSentAt()).isNull();

        relay.publishPendingEvents();

        verify(kafkaTemplate).send(eq("transfers.requested"), eq("key-1"), eq("{\"a\":1}"));
        verify(kafkaTemplate).send(eq("transfers.requested"), eq("key-2"), eq("{\"b\":2}"));
        // markSent() is called for both — JPA dirty-checking would flush these at commit.
        assertThat(e1.getSentAt()).isNotNull();
        assertThat(e2.getSentAt()).isNotNull();
    }

    @Test
    void kafkaFailureThrowsAndLeavesRowUnsent() {
        OutboxEvent e1 = newOutbox("transfers.requested", "key-fail", "{}");
        when(repository.lockBatchForPublish(anyInt())).thenReturn(List.of(e1));

        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker unreachable"));
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(failed);

        assertThatThrownBy(() -> relay.publishPendingEvents())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to publish outbox event");

        // markSent NOT called — the surrounding @Transactional will roll back at the
        // caller, sent_at stays NULL, and the next tick will retry this row.
        assertThat(e1.getSentAt()).isNull();
    }

    @Test
    void failureOnSecondRowLeavesFirstMarkedButSecondUnsent() {
        // Verifies that within a batch we don't blindly mark all rows sent — only the ones
        // whose send actually acked. The transaction rollback at the caller will then undo
        // the first one's mark too, so the next tick retries both. (We can't simulate the
        // rollback in this unit test; we just assert what the relay itself did.)
        OutboxEvent e1 = newOutbox("transfers.requested", "key-1", "{}");
        OutboxEvent e2 = newOutbox("transfers.requested", "key-2", "{}");
        when(repository.lockBatchForPublish(anyInt())).thenReturn(List.of(e1, e2));

        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker down"));
        when(kafkaTemplate.send(eq("transfers.requested"), eq("key-1"), any())).thenReturn(completedSend());
        when(kafkaTemplate.send(eq("transfers.requested"), eq("key-2"), any())).thenReturn(failed);

        assertThatThrownBy(() -> relay.publishPendingEvents())
                .isInstanceOf(IllegalStateException.class);

        assertThat(e1.getSentAt()).isNotNull();     // first one's send acked before the failure
        assertThat(e2.getSentAt()).isNull();        // second one never acked
    }

    private OutboxEvent newOutbox(String topic, String aggregateId, String payload) {
        return new OutboxEvent(UUID.randomUUID(), topic, aggregateId, payload);
    }

    private CompletableFuture<SendResult<String, String>> completedSend() {
        // KafkaTemplate.send returns CompletableFuture<SendResult<K,V>>. The relay only
        // calls .get() to wait — it doesn't inspect the result — so we can hand back any
        // SendResult. RecordMetadata's constructor in 3.9.x takes (TopicPartition, baseOffset,
        // batchIndex, timestamp, serializedKeySize, serializedValueSize).
        RecordMetadata md = new RecordMetadata(new TopicPartition("t", 0), 0L, 0, 0L, 0, 0);
        return CompletableFuture.completedFuture(new SendResult<>(null, md));
    }
}
