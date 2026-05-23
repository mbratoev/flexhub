package com.example.flexhub.account;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// Seeds three deterministic accounts ON FIRST START — skipped if already present so
// restarts preserve balances. The third account is flagged as rejects_credits=true
// to deterministically trigger the saga compensation path in demos.
@Component
class AccountDataInitializer {

    private static final Logger log = LoggerFactory.getLogger(AccountDataInitializer.class);

    static final UUID ALICE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID BOB_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    // Carol's account always rejects credits — sending money TO her triggers CreditFailed,
    // which triggers the compensation flow that re-credits the source.
    static final UUID CAROL_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private final AccountService service;

    AccountDataInitializer(AccountService service) {
        this.service = service;
    }

    @PostConstruct
    void seed() {
        seedIfMissing(ALICE_ID, "Alice", "1000.00", false);
        seedIfMissing(BOB_ID,   "Bob",    "500.00", false);
        seedIfMissing(CAROL_ID, "Carol (rejects-credits)", "0.00", true);
    }

    private void seedIfMissing(UUID id, String name, String balance, boolean rejectsCredits) {
        if (service.findById(id).isPresent()) {
            log.info("{} already present, leaving untouched", name);
            return;
        }
        service.save(new Account(id, name, new BigDecimal(balance), rejectsCredits));
        log.info("Seeded {} with balance {}, rejectsCredits={}", id, balance, rejectsCredits);
    }
}
