package com.example.flexhub.account;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatementEntryRepository extends JpaRepository<StatementEntry, UUID> {
    List<StatementEntry> findByAccountIdOrderByOccurredAtDesc(UUID accountId);
}
