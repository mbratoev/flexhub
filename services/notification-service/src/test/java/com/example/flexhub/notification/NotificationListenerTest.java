package com.example.flexhub.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.flexhub.events.AccountCredited;
import com.example.flexhub.events.DebitRejected;
import com.example.flexhub.events.TransferReversed;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotificationListenerTest {

    private NotificationRepository notifications;
    private ProcessedEventRepository processedEvents;
    private NotificationListener listener;

    private final UUID eventId = UUID.randomUUID();
    private final UUID transferId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        notifications = mock(NotificationRepository.class);
        processedEvents = mock(ProcessedEventRepository.class);
        listener = new NotificationListener(notifications, processedEvents);
        when(processedEvents.recordIfFirstSeen(any(), any())).thenReturn(1);
    }

    @Test
    void accountCreditedCreatesCompletedNotification() {
        listener.onAccountCredited(new AccountCredited(eventId, transferId,
                UUID.randomUUID(), new BigDecimal("100.00")));

        Notification saved = captureSaved();
        assertThat(saved.getType()).isEqualTo(Notification.Type.TRANSFER_COMPLETED);
        assertThat(saved.getTransferId()).isEqualTo(transferId);
        assertThat(saved.getBody()).contains("completed").contains("100.00");
    }

    @Test
    void debitRejectedCreatesFailedNotification() {
        listener.onDebitRejected(new DebitRejected(eventId, transferId,
                DebitRejected.Reason.INSUFFICIENT_FUNDS, "balance 5 below 100"));

        Notification saved = captureSaved();
        assertThat(saved.getType()).isEqualTo(Notification.Type.TRANSFER_FAILED);
        assertThat(saved.getBody()).contains("INSUFFICIENT_FUNDS").contains("balance 5 below 100");
    }

    @Test
    void transferReversedCreatesReversedNotification() {
        UUID sourceId = UUID.randomUUID();
        listener.onTransferReversed(new TransferReversed(eventId, transferId, sourceId,
                new BigDecimal("100.00"), "DESTINATION_REJECTS_CREDITS: ..."));

        Notification saved = captureSaved();
        assertThat(saved.getType()).isEqualTo(Notification.Type.TRANSFER_REVERSED);
        assertThat(saved.getBody()).contains("refunded").contains(sourceId.toString());
    }

    @Test
    void redeliveredEventIsSkippedEntirely() {
        when(processedEvents.recordIfFirstSeen(eq(eventId), any())).thenReturn(0);

        listener.onAccountCredited(new AccountCredited(eventId, transferId,
                UUID.randomUUID(), new BigDecimal("100.00")));

        verifyNoInteractions(notifications);
    }

    private Notification captureSaved() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(captor.capture());
        return captor.getValue();
    }
}
