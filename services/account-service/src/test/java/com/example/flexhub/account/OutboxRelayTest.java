package com.example.flexhub.account;

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

// Mirrors transaction-service's OutboxRelayTest. Same shape, same assertions —
// the relay code is logically identical between the two services.
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
        OutboxEvent e1 = newOutbox("accounts.debited", "k1", "{}");
        OutboxEvent e2 = newOutbox("accounts.credited", "k2", "{}");
        when(repository.lockBatchForPublish(anyInt())).thenReturn(List.of(e1, e2));
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(completedSend());

        relay.publishPendingEvents();

        verify(kafkaTemplate).send(eq("accounts.debited"), eq("k1"), eq("{}"));
        verify(kafkaTemplate).send(eq("accounts.credited"), eq("k2"), eq("{}"));
        assertThat(e1.getSentAt()).isNotNull();
        assertThat(e2.getSentAt()).isNotNull();
    }

    @Test
    void kafkaFailureThrowsAndLeavesRowUnsent() {
        OutboxEvent e1 = newOutbox("accounts.debited", "k", "{}");
        when(repository.lockBatchForPublish(anyInt())).thenReturn(List.of(e1));

        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker unreachable"));
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(failed);

        assertThatThrownBy(() -> relay.publishPendingEvents())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to publish outbox event");

        assertThat(e1.getSentAt()).isNull();
    }

    private OutboxEvent newOutbox(String topic, String aggregateId, String payload) {
        return new OutboxEvent(UUID.randomUUID(), topic, aggregateId, payload);
    }

    private CompletableFuture<SendResult<String, String>> completedSend() {
        RecordMetadata md = new RecordMetadata(new TopicPartition("t", 0), 0L, 0, 0L, 0, 0);
        return CompletableFuture.completedFuture(new SendResult<>(null, md));
    }
}
