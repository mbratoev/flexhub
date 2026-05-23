package com.example.flexhub.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.flexhub.events.AccountCredited;
import com.example.flexhub.events.AccountDebited;
import com.example.flexhub.events.CreditFailed;
import com.example.flexhub.events.DebitRejected;
import com.example.flexhub.events.TransferReversed;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// Exercises every state transition of the saga state machine in transaction-service.
// Pure Mockito — no Spring context, no broker, no DB.
class TransferSagaListenerTest {

    private TransactionRepository repository;
    private ProcessedEventRepository processedEvents;
    private TransferSagaListener listener;

    @BeforeEach
    void setUp() {
        repository = mock(TransactionRepository.class);
        processedEvents = mock(ProcessedEventRepository.class);
        listener = new TransferSagaListener(repository, processedEvents);
        when(processedEvents.recordIfFirstSeen(any(), any())).thenReturn(1);
    }

    @Test
    void onAccountDebited_marksDebited() {
        Transaction t = pending();
        when(repository.findById(t.getId())).thenReturn(Optional.of(t));

        listener.onAccountDebited(new AccountDebited(UUID.randomUUID(), t.getId(),
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100.00")));

        assertThat(t.getState()).isEqualTo(Transaction.State.DEBITED);
    }

    @Test
    void onAccountCredited_marksCompleted() {
        Transaction t = pending();
        t.markDebited();
        when(repository.findById(t.getId())).thenReturn(Optional.of(t));

        listener.onAccountCredited(new AccountCredited(UUID.randomUUID(), t.getId(),
                UUID.randomUUID(), new BigDecimal("100.00")));

        assertThat(t.getState()).isEqualTo(Transaction.State.COMPLETED);
        assertThat(t.getReason()).isNull();
    }

    @Test
    void onDebitRejected_marksFailedWithReason() {
        Transaction t = pending();
        when(repository.findById(t.getId())).thenReturn(Optional.of(t));

        listener.onDebitRejected(new DebitRejected(UUID.randomUUID(), t.getId(),
                DebitRejected.Reason.INSUFFICIENT_FUNDS, "balance 5 below 100"));

        assertThat(t.getState()).isEqualTo(Transaction.State.FAILED);
        assertThat(t.getReason()).contains("INSUFFICIENT_FUNDS").contains("balance 5 below 100");
    }

    @Test
    void onCreditFailed_marksReversedAwaitingRefund() {
        Transaction t = pending();
        t.markDebited();
        when(repository.findById(t.getId())).thenReturn(Optional.of(t));

        listener.onCreditFailed(new CreditFailed(UUID.randomUUID(), t.getId(),
                UUID.randomUUID(), new BigDecimal("100.00"),
                CreditFailed.Reason.DESTINATION_REJECTS_CREDITS, "destination rejects credits"));

        assertThat(t.getState()).isEqualTo(Transaction.State.REVERSED);
        assertThat(t.getReason()).contains("DESTINATION_REJECTS_CREDITS");
    }

    @Test
    void onTransferReversed_marksFailedAfterCompensation() {
        Transaction t = pending();
        t.markDebited();
        t.markReversed("destination rejected");
        when(repository.findById(t.getId())).thenReturn(Optional.of(t));

        listener.onTransferReversed(new TransferReversed(UUID.randomUUID(), t.getId(),
                UUID.randomUUID(), new BigDecimal("100.00"), "DESTINATION_REJECTS_CREDITS: ..."));

        assertThat(t.getState()).isEqualTo(Transaction.State.FAILED);
        assertThat(t.getReason()).contains("REVERSED").contains("DESTINATION_REJECTS_CREDITS");
    }

    @Test
    void redeliveredEventIsSkippedEntirely() {
        UUID eventId = UUID.randomUUID();
        when(processedEvents.recordIfFirstSeen(eq(eventId), any())).thenReturn(0);

        listener.onAccountDebited(new AccountDebited(eventId, UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100.00")));

        verifyNoInteractions(repository);
    }

    @Test
    void eventForUnknownTransactionIsIgnoredSilently() {
        UUID unknown = UUID.randomUUID();
        when(repository.findById(unknown)).thenReturn(Optional.empty());

        listener.onAccountDebited(new AccountDebited(UUID.randomUUID(), unknown,
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100.00")));

        // No exception, no mutation — just a warning log line.
    }

    private Transaction pending() {
        return new Transaction(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("100.00"));
    }
}
