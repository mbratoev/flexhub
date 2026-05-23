package com.example.flexhub.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.flexhub.events.Topics;
import com.example.flexhub.transaction.TransferController.CreateTransferRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

class TransferControllerTest extends AbstractIntegrationTest {

    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private OutboxEventRepository outboxRepository;
    @Autowired private TransactionRepository transactionRepository;

    @Test
    void postTransferReturns202PendingAndWritesOutboxRow() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        UUID source = UUID.randomUUID();
        UUID dest = UUID.randomUUID();
        String payload = objectMapper.writeValueAsString(
                new CreateTransferRequest(source, dest, new BigDecimal("100.00")));

        MvcResult result = mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.state").value("PENDING"))
                .andExpect(jsonPath("$.amount").value(100.00))
                .andExpect(jsonPath("$.id").isString())
                .andReturn();

        String transferId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText();

        List<OutboxEvent> all = outboxRepository.findAll();
        OutboxEvent forThisTransfer = all.stream()
                .filter(o -> o.getAggregateId().equals(transferId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No outbox row for transfer " + transferId));

        assertThat(forThisTransfer.getTopic()).isEqualTo(Topics.TRANSFERS_REQUESTED);
        assertThat(forThisTransfer.getSentAt()).isNull();
        assertThat(forThisTransfer.getPayload())
                .contains(transferId)
                .contains(source.toString())
                .contains(dest.toString())
                .contains("100.00");
    }

    @Test
    void idempotencyKeyReturnsSameTransactionOnRetry() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        String idempotencyKey = "test-key-" + UUID.randomUUID();
        String payload = objectMapper.writeValueAsString(
                new CreateTransferRequest(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("42.00")));

        MvcResult first = mockMvc.perform(post("/transfers")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isAccepted())
                .andReturn();
        String firstId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();

        // Second POST with same key — should return the original id, no new transaction.
        long countBefore = transactionRepository.count();

        MvcResult second = mockMvc.perform(post("/transfers")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isAccepted())
                .andReturn();
        String secondId = objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asText();

        assertThat(secondId).isEqualTo(firstId);
        assertThat(transactionRepository.count()).isEqualTo(countBefore);

        // Even sending a DIFFERENT body with the same key should not create a new transaction —
        // the key is the dedup criterion, the body is ignored on retry.
        String differentPayload = objectMapper.writeValueAsString(
                new CreateTransferRequest(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("999.00")));
        MvcResult third = mockMvc.perform(post("/transfers")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(differentPayload))
                .andExpect(status().isAccepted())
                .andReturn();
        assertThat(objectMapper.readTree(third.getResponse().getContentAsString()).get("id").asText())
                .isEqualTo(firstId);
    }

    @Test
    void getUnknownTransferReturns404() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        mockMvc.perform(get("/transfers/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
    }
}
