package com.example.flexhub.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.flexhub.events.AccountCredited;
import com.example.flexhub.events.AccountDebited;
import com.example.flexhub.events.CreditFailed;
import com.example.flexhub.events.Topics;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CreditListenerTest {

    private AccountService accountService;
    private ProcessedEventRepository processedEvents;
    private OutboxPublisher outbox;
    private CreditListener listener;

    private final UUID eventId = UUID.randomUUID();
    private final UUID transferId = UUID.randomUUID();
    private final UUID sourceId = UUID.randomUUID();
    private final UUID destId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        accountService = mock(AccountService.class);
        processedEvents = mock(ProcessedEventRepository.class);
        outbox = mock(OutboxPublisher.class);
        listener = new CreditListener(accountService, processedEvents, outbox);
        when(processedEvents.recordIfFirstSeen(any(), any())).thenReturn(1);
    }

    private AccountDebited incoming() {
        return new AccountDebited(eventId, transferId, sourceId, destId, new BigDecimal("100.00"));
    }

    @Test
    void redeliveredEventIsSkippedEntirely() {
        when(processedEvents.recordIfFirstSeen(eq(eventId), any())).thenReturn(0);

        listener.onAccountDebited(incoming());

        verifyNoInteractions(accountService);
        verifyNoInteractions(outbox);
    }

    @Test
    void successfulCreditEmitsAccountCredited() {
        when(accountService.credit(destId, new BigDecimal("100.00")))
                .thenReturn(AccountService.CreditResult.success(
                        new Account(destId, "Bob", new BigDecimal("600.00"))));

        listener.onAccountDebited(incoming());

        AccountCredited emitted = capture(Topics.ACCOUNTS_CREDITED, AccountCredited.class);
        assertThat(emitted.transferId()).isEqualTo(transferId);
        assertThat(emitted.destinationAccountId()).isEqualTo(destId);
    }

    @Test
    void missingDestinationEmitsCreditFailedDestinationNotFound() {
        when(accountService.credit(eq(destId), any())).thenReturn(AccountService.CreditResult.accountNotFound());

        listener.onAccountDebited(incoming());

        CreditFailed emitted = capture(Topics.ACCOUNTS_CREDIT_FAILED, CreditFailed.class);
        assertThat(emitted.reason()).isEqualTo(CreditFailed.Reason.DESTINATION_ACCOUNT_NOT_FOUND);
        assertThat(emitted.sourceAccountId()).isEqualTo(sourceId);
        assertThat(emitted.detail()).contains(destId.toString());
    }

    @Test
    void rejectsCreditsEmitsCreditFailedDestinationRejects() {
        when(accountService.credit(eq(destId), any())).thenReturn(AccountService.CreditResult.rejectsCredits());

        listener.onAccountDebited(incoming());

        CreditFailed emitted = capture(Topics.ACCOUNTS_CREDIT_FAILED, CreditFailed.class);
        assertThat(emitted.reason()).isEqualTo(CreditFailed.Reason.DESTINATION_REJECTS_CREDITS);
        assertThat(emitted.sourceAccountId()).isEqualTo(sourceId);
    }

    private <T> T capture(String expectedTopic, Class<T> expectedType) {
        ArgumentCaptor<UUID> idCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outbox).publish(idCaptor.capture(), topicCaptor.capture(), keyCaptor.capture(), payloadCaptor.capture());
        assertThat(topicCaptor.getValue()).isEqualTo(expectedTopic);
        assertThat(keyCaptor.getValue()).isEqualTo(transferId.toString());
        assertThat(payloadCaptor.getValue()).isInstanceOf(expectedType);
        return expectedType.cast(payloadCaptor.getValue());
    }
}
