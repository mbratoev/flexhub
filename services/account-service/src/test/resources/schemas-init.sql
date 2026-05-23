-- Pre-creates the schema for Testcontainers Postgres so Flyway's history table has somewhere to live.
-- Mirrors the role of infra/postgres/init.sql in production.
CREATE SCHEMA IF NOT EXISTS accounts;
