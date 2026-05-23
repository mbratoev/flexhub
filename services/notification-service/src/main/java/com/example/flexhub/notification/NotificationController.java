package com.example.flexhub.notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Read-only REST surface for inspecting what notifications would have been sent.
// In real systems this view typically wouldn't be public — operators would query the DB.
// We expose it because it's useful for the demo / smoke tests.
@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationRepository repository;

    public NotificationController(NotificationRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/{transferId}")
    public ResponseEntity<List<NotificationResponse>> byTransfer(@PathVariable UUID transferId) {
        List<NotificationResponse> rows = repository.findByTransferIdOrderBySentAtAsc(transferId).stream()
                .map(NotificationResponse::from)
                .toList();
        return ResponseEntity.ok(rows);
    }

    public record NotificationResponse(UUID id, UUID transferId, String type, String body, Instant sentAt) {
        static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getTransferId(),
                    n.getType().name(), n.getBody(), n.getSentAt());
        }
    }
}
