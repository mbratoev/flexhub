package com.example.flexhub.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.flexhub.events.Topics;
import com.example.flexhub.transaction.TransferController.CreateTransferRequest;
import com.example.flexhub.transaction.TransferController.TransferResponse;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

// Exercises OutboxRelay against a REAL Postgres + REAL Kafka. Complements the mock-based
// OutboxRelayTest by verifying:
//   - `SELECT ... FOR UPDATE SKIP LOCKED` works on the real broker dialect
//   - The relay actually puts a message on the wire reachable by an external consumer
//   - sent_at is committed (subsequent ticks don't republish)
class OutboxRelayIntegrationTest extends AbstractEndToEndIntegrationTest {

    @Autowired private TestRestTemplate rest;
    @Autowired private OutboxEventRepository outboxRepository;

    @Test
    void postTransferIsRelayedToKafkaAndMarkedSent() throws Exception {
        UUID src = UUID.randomUUID();
        UUID dst = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("123.45");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<TransferResponse> response = rest.postForEntity(
                "/transfers", new HttpEntity<>(new CreateTransferRequest(src, dst, amount), headers),
                TransferResponse.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        UUID transferId = response.getBody().id();

        // The @Scheduled relay polls every 100ms; the row should be marked sent quickly.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(outboxRepository.findAll().stream()
                        .filter(o -> o.getAggregateId().equals(transferId.toString()))
                        .findFirst()
                        .orElseThrow()
                        .getSentAt())
                        .as("outbox row sent_at marked")
                        .isNotNull());

        // And the event is observable on the wire.
        try (KafkaConsumer<String, String> consumer = newStringConsumer("outbox-it-" + UUID.randomUUID())) {
            consumer.subscribe(java.util.List.of(Topics.TRANSFERS_REQUESTED));
            AtomicReference<ConsumerRecord<String, String>> match = new AtomicReference<>();
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                consumer.poll(Duration.ofMillis(500))
                        .records(Topics.TRANSFERS_REQUESTED)
                        .forEach(r -> {
                            if (r.key().equals(transferId.toString())) {
                                match.set(r);
                            }
                        });
                assertThat(match.get()).as("transfer event observed on topic").isNotNull();
            });

            assertThat(match.get().value())
                    .contains(transferId.toString())
                    .contains(src.toString())
                    .contains(dst.toString())
                    .contains("123.45");
        }
    }

    private KafkaConsumer<String, String> newStringConsumer(String groupId) {
        Map<String, Object> props = new HashMap<>();
        // @Value("${spring.kafka.bootstrap-servers}") would read the application.yml default
        // (localhost:9092) — @ServiceConnection populates KafkaConnectionDetails but not
        // the property source. Pull the bootstrap straight off the container.
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return new KafkaConsumer<>(props);
    }
}
