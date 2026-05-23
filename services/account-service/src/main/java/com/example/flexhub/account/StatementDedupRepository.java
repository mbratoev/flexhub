package com.example.flexhub.account;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StatementDedupRepository extends JpaRepository<StatementDedup, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO accounts.statement_dedup (event_id, topic, processed_at)
            VALUES (:eventId, :topic, NOW())
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int recordIfFirstSeen(@Param("eventId") UUID eventId, @Param("topic") String topic);
}
