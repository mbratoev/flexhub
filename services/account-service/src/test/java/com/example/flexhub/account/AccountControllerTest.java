package com.example.flexhub.account;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

// Integration test: real Postgres via Testcontainers (see AbstractIntegrationTest),
// real Flyway migrations applied, real JPA repository, in-process HTTP via MockMvc.
class AccountControllerTest extends AbstractIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createAndFetchAccount() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        // Random holder name + balance to keep this test independent of the seed data
        // that AccountDataInitializer inserts on startup.
        String holder = "Test-" + UUID.randomUUID();
        String payload = objectMapper.writeValueAsString(
                new AccountController.CreateAccountRequest(holder, new BigDecimal("100.00")));

        MvcResult createResult = mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", notNullValue()))
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.holderName").value(holder))
                .andExpect(jsonPath("$.balance").value(100.00))
                .andReturn();

        AccountController.AccountResponse created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), AccountController.AccountResponse.class);

        mockMvc.perform(get("/accounts/" + created.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holderName").value(holder))
                .andExpect(jsonPath("$.balance").value(100.00));
    }

    @Test
    void getUnknownAccountReturns404() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        mockMvc.perform(get("/accounts/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
    }
}
