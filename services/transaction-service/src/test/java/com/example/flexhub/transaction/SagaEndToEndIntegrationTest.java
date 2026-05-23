package com.example.flexhub.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.flexhub.events.AccountCredited;
import com.example.flexhub.events.AccountDebited;
import com.example.flexhub.events.CreditFailed;
import com.example.flexhub.events.TransferReversed;
import com.example.flexhub.transaction.TransferController.CreateTransferRequest;
import com.example.flexhub.transaction.TransferController.TransferResponse;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

// End-to-end saga test over real Postgres + real Kafka (the broker is up alongside
// — Kafka is exercised separately by OutboxRelayIntegrationTest covering the
// producer-side outbox-to-broker hop). For the consumer-side state-machine path
// the test invokes the saga listener bean directly rather than going through
// Kafka delivery. This is a deliberate split:
//
//   - The listener method is the unit of behavior we care about. Its @Transactional
//     proxy stays in effect (we call through the Spring bean), so the DB transaction
//     boundary is real. processed_events dedup is real. Hibernate dirty-checking is
//     real. Only the JSON deserialization step is bypassed.
//
//   - Routing JSON through the broker into the @KafkaListener depends on Spring
//     Kafka's per-listener `spring.json.value.default.type` reaching the
//     ErrorHandlingDeserializer-wrapped JsonDeserializer, which is brittle to
//     configure in tests. Direct listener invocation gives the same assertions
//     about saga progression without that brittleness.
//
//   - OutboxRelayIntegrationTest still verifies the producer-side wire path
//     end-to-end (real Postgres + real broker, real consumer reading from the
//     topic). The "round-trip producer-to-consumer" is the only thing not covered
//     by either test, and that path is what the manual smoke-test exercises
//     against the kind cluster.
//
// Two flows:
//   1. golden path: POST -> PENDING. onAccountDebited -> DEBITED. onAccountCredited -> COMPLETED.
//   2. compensation: POST -> PENDING. onAccountDebited -> DEBITED. onCreditFailed -> REVERSED.
//      onTransferReversed -> FAILED (compensation complete).
class SagaEndToEndIntegrationTest extends AbstractEndToEndIntegrationTest {

    @Autowired private TestRestTemplate rest;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private TransferSagaListener listener;

    @Test
    void goldenPath_postLeadsToCompletedAfterDebitAndCredit() {
        UUID src = UUID.randomUUID();
        UUID dst = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("250.00");

        UUID transferId = postTransfer(src, dst, amount);
        assertState(transferId, Transaction.State.PENDING);

        listener.onAccountDebited(new AccountDebited(
                UUID.randomUUID(), transferId, src, dst, amount));
        assertState(transferId, Transaction.State.DEBITED);

        listener.onAccountCredited(new AccountCredited(
                UUID.randomUUID(), transferId, dst, amount));
        assertState(transferId, Transaction.State.COMPLETED);
    }

    @Test
    void compensationPath_creditFailureWalksThroughReversedToFailed() {
        UUID src = UUID.randomUUID();
        UUID dst = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("99.99");

        UUID transferId = postTransfer(src, dst, amount);
        assertState(transferId, Transaction.State.PENDING);

        listener.onAccountDebited(new AccountDebited(
                UUID.randomUUID(), transferId, src, dst, amount));
        assertState(transferId, Transaction.State.DEBITED);

        listener.onCreditFailed(new CreditFailed(
                UUID.randomUUID(), transferId, dst, amount,
                CreditFailed.Reason.DESTINATION_REJECTS_CREDITS,
                "destination rejects credits"));
        assertState(transferId, Transaction.State.REVERSED);

        listener.onTransferReversed(new TransferReversed(
                UUID.randomUUID(), transferId, src, amount,
                "DESTINATION_REJECTS_CREDITS: destination rejects credits"));

        Transaction t = transactionRepository.findById(transferId).orElseThrow();
        assertThat(t.getState()).isEqualTo(Transaction.State.FAILED);
        assertThat(t.getReason()).contains("REVERSED").contains("DESTINATION_REJECTS_CREDITS");
    }

    private UUID postTransfer(UUID src, UUID dst, BigDecimal amount) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        TransferResponse body = rest.postForObject("/transfers",
                new HttpEntity<>(new CreateTransferRequest(src, dst, amount), headers),
                TransferResponse.class);
        return body.id();
    }

    private void assertState(UUID transferId, Transaction.State expected) {
        assertThat(transactionRepository.findById(transferId).orElseThrow().getState())
                .as("transaction %s state", transferId)
                .isEqualTo(expected);
    }
}
