package com.example.flexhub.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.flexhub.events.AccountDebited;
import com.example.flexhub.events.DebitRejected;
import com.example.flexhub.events.Topics;
import com.example.flexhub.events.TransferRequested;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

// Pure unit test for the saga's debit step. Mocks AccountService + ProcessedEventRepository
// + OutboxPublisher and verifies the right outbox event was queued for each debit outcome.
class TransferRequestListenerTest {

    private AccountService accountService;
    private ProcessedEventRepository processedEvents;
    private OutboxPublisher outbox;
    private TransferRequestListener listener;

    private final UUID eventId = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    private final UUID transferId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private final UUID sourceId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID destId = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeEach
    void setUp() {
        accountService = mock(AccountService.class);
        processedEvents = mock(ProcessedEventRepository.class);
        outbox = mock(OutboxPublisher.class);
        listener = new TransferRequestListener(accountService, processedEvents, outbox);

        when(processedEvents.recordIfFirstSeen(any(), any())).thenReturn(1);
    }

    private TransferRequested incoming(BigDecimal amount) {
        return new TransferRequested(eventId, transferId, sourceId, destId, amount);
    }

    @Test
    void redeliveredEventIsSkippedEntirely() {
        when(processedEvents.recordIfFirstSeen(eq(eventId), any())).thenReturn(0);

        listener.onTransferRequested(incoming(new BigDecimal("100.00")));

        verifyNoInteractions(accountService);
        verifyNoInteractions(outbox);
    }

    @Test
    void successfulDebitEmitsAccountDebited() {
        when(accountService.debit(sourceId, new BigDecimal("100.00")))
                .thenReturn(AccountService.DebitResult.success(
                        new Account(sourceId, "Alice", new BigDecimal("900.00"))));

        listener.onTransferRequested(incoming(new BigDecimal("100.00")));

        AccountDebited emitted = captureEmittedTo(Topics.ACCOUNTS_DEBITED, AccountDebited.class);
        assertThat(emitted.transferId()).isEqualTo(transferId);
        assertThat(emitted.sourceAccountId()).isEqualTo(sourceId);
        assertThat(emitted.destinationAccountId()).isEqualTo(destId);
        assertThat(emitted.amount()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void insufficientFundsEmitsDebitRejected() {
        when(accountService.debit(eq(sourceId), any()))
                .thenReturn(AccountService.DebitResult.insufficientFunds(new BigDecimal("50.00")));

        listener.onTransferRequested(incoming(new BigDecimal("100.00")));

        DebitRejected emitted = captureEmittedTo(Topics.ACCOUNTS_DEBIT_REJECTED, DebitRejected.class);
        assertThat(emitted.reason()).isEqualTo(DebitRejected.Reason.INSUFFICIENT_FUNDS);
        assertThat(emitted.detail()).contains("50.00").contains("100");
    }

    @Test
    void missingSourceEmitsDebitRejected() {
        when(accountService.debit(eq(sourceId), any())).thenReturn(AccountService.DebitResult.accountNotFound());

        listener.onTransferRequested(incoming(new BigDecimal("100.00")));

        DebitRejected emitted = captureEmittedTo(Topics.ACCOUNTS_DEBIT_REJECTED, DebitRejected.class);
        assertThat(emitted.reason()).isEqualTo(DebitRejected.Reason.SOURCE_ACCOUNT_NOT_FOUND);
        assertThat(emitted.detail()).contains(sourceId.toString());
    }

    private <T> T captureEmittedTo(String expectedTopic, Class<T> expectedType) {
        ArgumentCaptor<UUID> idCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outbox).publish(idCaptor.capture(), topicCaptor.capture(), keyCaptor.capture(), payloadCaptor.capture());
        assertThat(topicCaptor.getValue()).isEqualTo(expectedTopic);
        assertThat(keyCaptor.getValue()).isEqualTo(transferId.toString());
        assertThat(idCaptor.getValue()).isNotNull();
        assertThat(payloadCaptor.getValue()).isInstanceOf(expectedType);
        return expectedType.cast(payloadCaptor.getValue());
    }
}
