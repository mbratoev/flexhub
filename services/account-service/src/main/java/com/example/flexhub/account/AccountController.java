package com.example.flexhub.account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService service;
    private final StatementEntryRepository statement;

    public AccountController(AccountService service, StatementEntryRepository statement) {
        this.service = service;
        this.statement = statement;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        Account account = service.save(new Account(UUID.randomUUID(), request.holderName(), request.initialBalance()));
        return ResponseEntity.created(URI.create("/accounts/" + account.getId()))
                .body(AccountResponse.from(account));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> get(@PathVariable UUID id) {
        return service.findById(id)
                .map(AccountResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // CQRS read-side endpoint. Reads ONLY from account_statement (the denormalized
    // projection), NEVER from the write tables. Returns ledger entries newest-first.
    @GetMapping("/{id}/statement")
    public ResponseEntity<List<StatementResponse>> statement(@PathVariable UUID id) {
        if (service.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        List<StatementResponse> entries = statement.findByAccountIdOrderByOccurredAtDesc(id).stream()
                .map(StatementResponse::from)
                .toList();
        return ResponseEntity.ok(entries);
    }

    public record CreateAccountRequest(
            @NotBlank String holderName,
            @NotNull @PositiveOrZero BigDecimal initialBalance) {
    }

    public record AccountResponse(UUID id, String holderName, BigDecimal balance) {
        static AccountResponse from(Account a) {
            return new AccountResponse(a.getId(), a.getHolderName(), a.getBalance());
        }
    }

    public record StatementResponse(UUID id, UUID transferId, BigDecimal delta, String reason, Instant occurredAt) {
        static StatementResponse from(StatementEntry s) {
            return new StatementResponse(s.getId(), s.getTransferId(), s.getDelta(), s.getReason(), s.getOccurredAt());
        }
    }
}
