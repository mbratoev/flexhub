package com.example.flexhub.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.flexhub.events.CreditFailed;
import com.example.flexhub.events.Topics;
import com.example.flexhub.events.TransferReversed;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RefundListenerTest {

    private AccountService accountService;
    private ProcessedEventRepository processedEvents;
    private OutboxPublisher outbox;
    private RefundListener listener;

    private final UUID eventId = UUID.randomUUID();
    private final UUID transferId = UUID.randomUUID();
    private final UUID sourceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        accountService = mock(AccountService.class);
        processedEvents = mock(ProcessedEventRepository.class);
        outbox = mock(OutboxPublisher.class);
        listener = new RefundListener(accountService, processedEvents, outbox);
        when(processedEvents.recordIfFirstSeen(any(), any())).thenReturn(1);
    }

    private CreditFailed incoming() {
        return new CreditFailed(eventId, transferId, sourceId, new BigDecimal("100.00"),
                CreditFailed.Reason.DESTINATION_REJECTS_CREDITS, "destination rejects credits");
    }

    @Test
    void redeliveredEventIsSkippedEntirely() {
        when(processedEvents.recordIfFirstSeen(eq(eventId), any())).thenReturn(0);

        listener.onCreditFailed(incoming());

        verifyNoInteractions(accountService);
        verifyNoInteractions(outbox);
    }

    @Test
    void refundsSourceAndEmitsTransferReversed() {
        when(accountService.creditUnconditional(sourceId, new BigDecimal("100.00")))
                .thenReturn(Optional.of(new Account(sourceId, "Alice", new BigDecimal("1000.00"))));

        listener.onCreditFailed(incoming());

        ArgumentCaptor<UUID> idCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outbox).publish(idCaptor.capture(), topicCaptor.capture(), keyCaptor.capture(), payloadCaptor.capture());

        assertThat(topicCaptor.getValue()).isEqualTo(Topics.TRANSFERS_REVERSED);
        assertThat(keyCaptor.getValue()).isEqualTo(transferId.toString());

        TransferReversed reversed = (TransferReversed) payloadCaptor.getValue();
        assertThat(reversed.transferId()).isEqualTo(transferId);
        assertThat(reversed.sourceAccountId()).isEqualTo(sourceId);
        assertThat(reversed.amount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(reversed.originalFailureReason()).contains("DESTINATION_REJECTS_CREDITS");
    }

    @Test
    void missingSourceLogsAndSkipsOutboxWithoutThrowing() {
        when(accountService.creditUnconditional(eq(sourceId), any())).thenReturn(Optional.empty());

        listener.onCreditFailed(incoming());

        // Don't throw, don't publish — the inconsistency is left for ops to investigate
        // (a real system would emit a "RefundFailed" alert event; deferred for now).
        verify(outbox, never()).publish(any(), any(), any(), any());
    }
}
