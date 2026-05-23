-- Runs once on first container start (postgres image picks up *.sql from /docker-entrypoint-initdb.d/).
-- Creates one schema per service so each service owns its tables independently
-- but they share the same Postgres instance.

CREATE SCHEMA IF NOT EXISTS accounts;
CREATE SCHEMA IF NOT EXISTS transactions;
CREATE SCHEMA IF NOT EXISTS notifications;

GRANT USAGE, CREATE ON SCHEMA accounts      TO flexhub;
GRANT USAGE, CREATE ON SCHEMA transactions  TO flexhub;
GRANT USAGE, CREATE ON SCHEMA notifications TO flexhub;
