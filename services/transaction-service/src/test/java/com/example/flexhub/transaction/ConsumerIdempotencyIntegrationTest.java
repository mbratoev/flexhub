package com.example.flexhub.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.flexhub.events.AccountDebited;
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

// Verifies consumer-side idempotency end-to-end with a real Postgres: re-delivering
// the same event (same eventId) does NOT re-apply the state transition. The dedup
// store is the `processed_events` table; the listener's first action is
// recordIfFirstSeen() which returns 0 for duplicates.
//
// See SagaEndToEndIntegrationTest for why we invoke the listener bean directly
// rather than routing JSON through the broker.
class ConsumerIdempotencyIntegrationTest extends AbstractEndToEndIntegrationTest {

    @Autowired private TestRestTemplate rest;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private ProcessedEventRepository processedEvents;
    @Autowired private TransferSagaListener listener;

    @Test
    void duplicateAccountDebitedEventDoesNotReapplyTransition() {
        UUID src = UUID.randomUUID();
        UUID dst = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("77.00");

        UUID transferId = postTransfer(src, dst, amount);

        // Single eventId, applied twice. The dedup table swallows the second call.
        UUID eventId = UUID.randomUUID();
        AccountDebited event = new AccountDebited(eventId, transferId, src, dst, amount);

        listener.onAccountDebited(event);
        assertThat(transactionRepository.findById(transferId).orElseThrow().getState())
                .isEqualTo(Transaction.State.DEBITED);

        long processedBefore = processedEvents.count();
        long transactionUpdatedBefore = transactionRepository.findById(transferId)
                .orElseThrow().getUpdatedAt().toEpochMilli();

        listener.onAccountDebited(event);

        assertThat(transactionRepository.findById(transferId).orElseThrow().getState())
                .as("redelivery must not change state")
                .isEqualTo(Transaction.State.DEBITED);
        assertThat(transactionRepository.findById(transferId).orElseThrow().getUpdatedAt().toEpochMilli())
                .as("redelivery must not bump updated_at (no second write)")
                .isEqualTo(transactionUpdatedBefore);
        assertThat(processedEvents.count())
                .as("duplicate eventId must not produce a second processed_events row")
                .isEqualTo(processedBefore);
    }

    private UUID postTransfer(UUID src, UUID dst, BigDecimal amount) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        TransferResponse body = rest.postForObject("/transfers",
                new HttpEntity<>(new CreateTransferRequest(src, dst, amount), headers),
                TransferResponse.class);
        return body.id();
    }
}
