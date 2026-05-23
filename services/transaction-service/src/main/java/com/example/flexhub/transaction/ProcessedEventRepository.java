package com.example.flexhub.transaction;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    // Postgres-specific `ON CONFLICT DO NOTHING` makes this idempotent at the SQL level:
    // - First time we see eventId, INSERT succeeds, returns 1.
    // - Redelivery of the same eventId, INSERT is silently skipped, returns 0.
    // No exception thrown either way. Caller branches on the return value.
    @Modifying
    @Query(value = """
            INSERT INTO transactions.processed_events (event_id, topic, processed_at)
            VALUES (:eventId, :topic, NOW())
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int recordIfFirstSeen(@Param("eventId") UUID eventId, @Param("topic") String topic);
}
