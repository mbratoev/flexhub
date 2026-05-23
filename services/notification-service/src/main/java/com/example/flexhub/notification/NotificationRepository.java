package com.example.flexhub.notification;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findByTransferIdOrderBySentAtAsc(UUID transferId);
}
