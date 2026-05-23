plugins {
    `java-library`
}

// Shared event records used by services that publish/consume each other's Kafka messages.
// Plain Java library (no Spring Boot plugin) — we don't want this module to be an app,
// just a typed schema of payloads. Services depend on it via `implementation(project(...))`.
