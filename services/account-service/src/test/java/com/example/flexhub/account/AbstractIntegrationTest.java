package com.example.flexhub.account;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// Shared base for any @SpringBootTest that needs a real Postgres + JPA + Flyway.
// Spring Boot 3.1+'s @ServiceConnection auto-wires the datasource — no @DynamicPropertySource needed.
// The container is `static` so it's reused across all subclasses in one JVM run.
@SpringBootTest
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            // Mirrors infra/postgres/init.sql in production — pre-creates the schema so
            // Flyway can place its history table in it.
            .withInitScript("schemas-init.sql");
}
