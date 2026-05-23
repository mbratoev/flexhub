package com.example.flexhub.transaction;

import com.example.flexhub.events.Topics;
import com.example.flexhub.events.TransferRequested;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transfers")
public class TransferController {

    private static final Logger log = LoggerFactory.getLogger(TransferController.class);

    private final TransactionRepository transactionRepository;
    private final OutboxEventRepository outboxRepository;
    private final IdempotencyKeyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    public TransferController(TransactionRepository transactionRepository,
                              OutboxEventRepository outboxRepository,
                              IdempotencyKeyRepository idempotencyRepository,
                              ObjectMapper objectMapper) {
        this.transactionRepository = transactionRepository;
        this.outboxRepository = outboxRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.objectMapper = objectMapper;
    }

    // Three-step idempotency flow:
    //   1. If the client sent `Idempotency-Key` and we've seen it, return the original
    //      transaction's CURRENT state (which may have advanced past PENDING by now).
    //   2. Otherwise create the transaction + outbox event in one DB tx.
    //   3. If we got a key, save it pointing at the new transaction id. If two requests
    //      with the same key race past step 1 and both reach step 3, the PRIMARY KEY
    //      constraint on idempotency_keys makes one of them fail — the @Transactional
    //      rolls back everything, no orphan transaction. Client retries and hits step 1.
    @PostMapping
    @Transactional
    public ResponseEntity<TransferResponse> create(
            @Valid @RequestBody CreateTransferRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        if (idempotencyKey != null) {
            Optional<IdempotencyKey> existing = idempotencyRepository.findById(idempotencyKey);
            if (existing.isPresent()) {
                UUID cachedTransactionId = existing.get().getTransactionId();
                Transaction cached = transactionRepository.findById(cachedTransactionId).orElseThrow();
                log.info("Idempotency-Key {} → returning cached transaction {}", idempotencyKey, cachedTransactionId);
                return ResponseEntity.accepted()
                        .location(URI.create("/transfers/" + cachedTransactionId))
                        .body(TransferResponse.from(cached));
            }
        }

        UUID transferId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        Transaction transaction = new Transaction(transferId,
                request.sourceAccountId(), request.destinationAccountId(), request.amount());
        transactionRepository.save(transaction);

        TransferRequested event = new TransferRequested(eventId, transferId,
                transaction.getSourceAccountId(), transaction.getDestinationAccountId(),
                transaction.getAmount());

        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize event", e);
        }

        outboxRepository.save(new OutboxEvent(eventId, Topics.TRANSFERS_REQUESTED,
                transferId.toString(), payload));

        if (idempotencyKey != null) {
            idempotencyRepository.save(new IdempotencyKey(idempotencyKey, transferId));
        }

        log.info("Transfer {} persisted, outbox event {} queued (idempotency-key={})",
                transferId, eventId, idempotencyKey);

        return ResponseEntity.accepted()
                .location(URI.create("/transfers/" + transferId))
                .body(TransferResponse.from(transaction));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransferResponse> get(@PathVariable UUID id) {
        return transactionRepository.findById(id)
                .map(TransferResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record CreateTransferRequest(
            @NotNull UUID sourceAccountId,
            @NotNull UUID destinationAccountId,
            @NotNull @Positive BigDecimal amount) {
    }

    public record TransferResponse(
            UUID id,
            UUID sourceAccountId,
            UUID destinationAccountId,
            BigDecimal amount,
            Transaction.State state,
            String reason,
            Instant createdAt,
            Instant updatedAt) {

        static TransferResponse from(Transaction t) {
            return new TransferResponse(t.getId(), t.getSourceAccountId(), t.getDestinationAccountId(),
                    t.getAmount(), t.getState(), t.getReason(), t.getCreatedAt(), t.getUpdatedAt());
        }
    }
}
