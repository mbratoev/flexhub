package com.example.flexhub.notification;

import com.example.flexhub.events.AccountCredited;
import com.example.flexhub.events.DebitRejected;
import com.example.flexhub.events.Topics;
import com.example.flexhub.events.TransferReversed;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Notification-service is a pure consumer of terminal saga events. Three listeners,
// one per terminal outcome. Each persists a Notification row and logs a "would-send"
// line standing in for an actual email/SMS dispatch.
//
// Same idempotent-consumer pattern as the other services: processed_events dedup
// inside an @Transactional method so the notification row + dedup row commit atomically.
@Component
class NotificationListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);

    private final NotificationRepository notifications;
    private final ProcessedEventRepository processedEvents;

    NotificationListener(NotificationRepository notifications, ProcessedEventRepository processedEvents) {
        this.notifications = notifications;
        this.processedEvents = processedEvents;
    }

    @KafkaListener(topics = Topics.ACCOUNTS_CREDITED, groupId = "notification-service.completed",
            properties = "spring.json.value.default.type=com.example.flexhub.events.AccountCredited")
    @Transactional
    void onAccountCredited(AccountCredited event) {
        if (isDuplicate(event.eventId(), Topics.ACCOUNTS_CREDITED)) return;

        String body = "Transfer " + event.transferId() + " completed — " + event.amount()
                + " credited to account " + event.destinationAccountId();
        save(event.transferId(), Notification.Type.TRANSFER_COMPLETED, body);
    }

    @KafkaListener(topics = Topics.ACCOUNTS_DEBIT_REJECTED, groupId = "notification-service.debit-rejected",
            properties = "spring.json.value.default.type=com.example.flexhub.events.DebitRejected")
    @Transactional
    void onDebitRejected(DebitRejected event) {
        if (isDuplicate(event.eventId(), Topics.ACCOUNTS_DEBIT_REJECTED)) return;

        String body = "Transfer " + event.transferId() + " failed at debit step — "
                + event.reason() + ": " + event.detail();
        save(event.transferId(), Notification.Type.TRANSFER_FAILED, body);
    }

    @KafkaListener(topics = Topics.TRANSFERS_REVERSED, groupId = "notification-service.reversed",
            properties = "spring.json.value.default.type=com.example.flexhub.events.TransferReversed")
    @Transactional
    void onTransferReversed(TransferReversed event) {
        if (isDuplicate(event.eventId(), Topics.TRANSFERS_REVERSED)) return;

        String body = "Transfer " + event.transferId() + " reversed — " + event.amount()
                + " refunded to account " + event.sourceAccountId()
                + ". Original failure: " + event.originalFailureReason();
        save(event.transferId(), Notification.Type.TRANSFER_REVERSED, body);
    }

    private boolean isDuplicate(UUID eventId, String topic) {
        if (processedEvents.recordIfFirstSeen(eventId, topic) == 0) {
            log.info("Skipping redelivered event {} from {} (already processed)", eventId, topic);
            return true;
        }
        return false;
    }

    private void save(UUID transferId, Notification.Type type, String body) {
        Notification n = notifications.save(new Notification(UUID.randomUUID(), transferId, type, body));
        log.info("[mock-send] {} -> transfer {}: {}", type, transferId, body);
        // In production this would hand off to an email/SMS provider via SDK or HTTP call —
        // with retries, a separate outbox for "sent" status, etc. We just log.
    }
}
