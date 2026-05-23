package com.example.flexhub.transaction;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    // SKIP LOCKED is critical when more than one transaction-service instance is running:
    // each instance locks (and processes) a different batch of unsent rows, so they don't
    // double-publish. Postgres-specific (FOR UPDATE SKIP LOCKED), which is fine — we're on Postgres.
    // Native SQL because Hibernate's JPQL doesn't have a portable SKIP LOCKED.
    @Query(value = """
            SELECT * FROM transactions.outbox_event
            WHERE sent_at IS NULL
            ORDER BY created_at ASC
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockBatchForPublish(@Param("batchSize") int batchSize);
}
