package com.example.flexhub.transaction;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

// Base class for tests that exercise the saga end-to-end with a real broker.
//
// Differs from AbstractIntegrationTest in two ways:
//   1. Adds a Kafka container (confluentinc/cp-kafka, KRaft) alongside Postgres.
//      Confluent's image is what Testcontainers' ConfluentKafkaContainer is tested
//      against; the protocol surface is identical to apache Kafka so this doesn't
//      diverge from production behavior.
//   2. Overrides the test profile that disables Kafka listener auto-startup and
//      pushes OutboxRelay's @Scheduled out to an hour. Here we want the listener
//      to consume and the relay to publish without manual prodding.
//
// Container lifecycle — Singleton Containers pattern.
// We deliberately do NOT use @Testcontainers + @Container here. JUnit's extension
// starts/stops @Container fields per test class, so when class B inherits class A's
// static container, the container has already been stopped after A ran. Starting
// the containers in a static initializer makes them live for the whole JVM, which
// matches Spring's context-cache lifetime — both are reused across all test classes.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.admin.fail-fast=true",
        "app.outbox.initial-delay-ms=100",
        "app.outbox.poll-delay-ms=100"
})
public abstract class AbstractEndToEndIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine").withInitScript("schemas-init.sql");

    @ServiceConnection
    static final ConfluentKafkaContainer KAFKA =
            new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.0"));

    static {
        POSTGRES.start();
        KAFKA.start();
    }
}
