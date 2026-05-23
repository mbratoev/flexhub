# flexhub — distributed payments platform

[![CI](https://github.com/mbratoev/flexhub/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/mbratoev/flexhub/actions/workflows/ci.yml)

End-to-end demonstration of a Spring Boot microservices stack for a payments/banking domain — money transfers across accounts, modelled as a distributed transaction. Four services (`account-service`, `transaction-service`, `notification-service`, `api-gateway`) communicating asynchronously over Kafka, persisted in PostgreSQL, containerised, deployed both to a local `kind` Kubernetes cluster and to Azure AKS.

## Architectural patterns

The domain is chosen so each of the patterns a payments system needs lives in a natural place — not bolted on, but shaping the data model and event flow from the start.

| Pattern | Where it lives |
|---|---|
| **Eventual consistency** | `POST /transfers` returns `202 Accepted` with `state=PENDING`; client polls and sees `PROCESSING → COMPLETED` |
| **Transactional Outbox** | `transaction-service` writes a `Transaction` row and an `outbox_event` row in the **same DB transaction**; a `@Scheduled` relay publishes pending rows to Kafka |
| **Idempotency** | REST side: `Idempotency-Key` header + dedup table → retries return the original result. Consumer side: `processed_events` table → at-least-once delivery is safe |
| **Saga (choreography)** | Transfer state machine `PENDING → DEBITED → COMPLETED` (happy path) or `PENDING → DEBITED → REVERSED → FAILED` (compensation). Six events orchestrate the dance; no central coordinator |
| **CQRS** | Write model is normalised + transactional. Read model is a denormalised `account_statement` projection populated by consuming all balance-change events |

## Tech stack

| Layer | Choice |
|---|---|
| Language | Java 21 LTS (Gradle toolchain) |
| Framework | Spring Boot 3.5.x, Spring Cloud Gateway |
| Build | Gradle (Kotlin DSL), multi-module monorepo |
| Messaging | Apache Kafka (local) / Azure Event Hubs (cloud, Kafka-compatible) |
| Database | PostgreSQL 16 (schema per service in one instance) |
| Persistence | Flyway migrations + Spring Data JPA |
| Containers | Multi-stage Dockerfile with Spring Boot layered jars |
| Orchestration | Kubernetes — `kind` locally, AKS in Azure |
| Packaging | Helm (one shared chart, four releases) |
| IaC | Terraform (AKS + ACR + Event Hubs + Key Vault + Workload Identity) |
| CI/CD | GitHub Actions (build/test gate + image publish to GHCR) |
| Tests | JUnit 5 + Spring Boot Test + **Testcontainers** for integration |

## Architecture

```
                       ┌──────────────────┐
              client → │   api-gateway    │  Spring Cloud Gateway (8080)
                       └────────┬─────────┘
                                │ REST
            ┌───────────────────┼───────────────────┐
            ▼                   ▼                   ▼
   ┌────────────────┐  ┌────────────────┐  ┌────────────────────┐
   │ account-service│  │transaction-svc │  │ notification-svc   │
   │     :8081      │  │     :8082      │  │       :8083        │
   └────────┬───────┘  └────────┬───────┘  └─────────┬──────────┘
            │                   │                    │
            │          publishes/consumes            │
            └──────────────┐    │    ┌───────────────┘
                           ▼    ▼    ▼
                       ┌──────────────────┐
                       │   Kafka broker   │  6 topics
                       └──────────────────┘
                           │    │    │
            ┌──────────────┘    │    └───────────────┐
            ▼                   ▼                    ▼
       schema:accounts    schema:transactions   schema:notifications
                       (single PostgreSQL instance)
```

**Event flow (golden path)** — a money transfer:

1. Client `POST /transfers` with `Idempotency-Key` → gateway → `transaction-service`
2. `transaction-service` checks the idempotency table; if new, persists `Transaction(state=PENDING)` AND inserts `TransferRequested` into `outbox_event` — same DB transaction
3. Outbox relay picks up the row, publishes to Kafka, marks sent
4. `account-service` consumer checks `processed_events`; debits source, persists `AccountDebited` to its own outbox
5. Outbox relay publishes `AccountDebited` → `account-service` credits destination → emits `AccountCredited` (or `CreditFailed` on failure)
6. On `AccountCredited`: `transaction-service` marks `COMPLETED`, emits `TransferCompleted`
7. On `CreditFailed`: compensation handler re-credits source, marks `REVERSED → FAILED`, emits `TransferReversed`
8. `notification-service` consumes terminal events, persists mock notifications
9. Read side: every balance-change event also lands in the `account_statement` projection

Kafka topics: `transfers.requested`, `accounts.debited`, `accounts.credited`, `accounts.credit-failed`, `transfers.completed`, `transfers.reversed`.

## Repository layout

```
flexhub/
├── settings.gradle.kts             # multi-module includes
├── build.gradle.kts                # root: shared plugins, versions
├── gradle/libs.versions.toml       # version catalog
├── docker-compose.yml              # local Kafka + Postgres + 4 services
├── services/
│   ├── account-service/            # accounts + statement projection (CQRS read)
│   ├── transaction-service/        # transfers + outbox + saga state machine
│   ├── notification-service/       # consumes terminal saga events
│   └── api-gateway/                # Spring Cloud Gateway
├── libs/
│   └── events/                     # shared event POJOs (JSON)
├── infra/
│   ├── azure/                      # Terraform for AKS + ACR + Event Hubs + KV
│   ├── k8s/                        # kind cluster config + Bitnami chart values
│   └── postgres/                   # init SQL for the docker-compose Postgres
├── deploy/
│   ├── helm/
│   │   ├── flexhub-service/        # one shared chart for all four services
│   │   └── values/                 # per-release overrides (kind + azure)
│   ├── k8s/                        # raw manifests, useful for debugging
│   └── Makefile                    # kind install / azure aks-start / smoke tests
└── .github/workflows/              # ci.yml (test gate) + image.yml (GHCR publish)
```

## Port allocation

| Service | Local port |
|---|---|
| api-gateway | 8080 |
| account-service | 8081 |
| transaction-service | 8082 |
| notification-service | 8083 |

## Running the stack

Three equivalent stacks are available. Pick whichever fits the task — they share the same images, the same env-var contract, the same endpoints, only the runtime substrate differs. Run one at a time; host ports conflict.

### Option A — docker-compose

```bash
docker compose up -d --build
```

That builds the four service images (first time only, ~2 min) and starts seven containers:

| Container | Host port | Role |
|---|---|---|
| `api-gateway` | 8080 | Primary client entry point |
| `account-service` | 8081 | Accounts + statement projection |
| `transaction-service` | 8082 | Transfers + outbox + saga state |
| `notification-service` | 8083 | Consumes terminal saga events |
| `kafka` | 9092 | Apache Kafka, KRaft mode |
| `kafka-ui` | 8090 | Browser-based topic / message viewer |
| `postgres` | 5432 | All services' schemas in one DB |

All client traffic should normally go through the gateway on `localhost:8080`. The downstream ports are exposed for debugging.

**Iterating on one service:**

```bash
docker compose build account-service && docker compose up -d --force-recreate account-service
```

The Spring Boot layered-jar Docker layout means only the small `application` layer rebuilds.

**Hot-reload during active development** — run any subset on the host while keeping infra in containers:

```bash
docker compose up -d kafka kafka-ui postgres
./gradlew :services:account-service:bootRun
```

### Option B — local Kubernetes (`kind` + Helm)

Same images, same env vars, same endpoints — Kubernetes instead of compose.

```bash
docker compose down                  # stop compose first if running

make -C deploy install               # kind up, Bitnami kafka+postgres, helm install
make -C deploy port-forward          # localhost:8080 → api-gateway (separate terminal)
make -C deploy smoke-test            # golden path + compensation
make -C deploy uninstall             # tear down releases (cluster stays)
make -C deploy destroy               # destroy the kind cluster too
```

The shared chart in [`deploy/helm/flexhub-service/`](deploy/helm/flexhub-service/) is rendered four times with per-release overrides in [`deploy/helm/values/kind/`](deploy/helm/values/kind/). Raw manifests in [`deploy/k8s/`](deploy/k8s/) are equivalent and useful for the "what does the chart actually produce" debugging case.

### Option C — Azure AKS

Same images and chart as `kind`, but against Azure Event Hubs (Kafka-compatible) and Postgres running in-cluster (Bitnami chart).

```bash
# Bring up from a fully-destroyed state (~25-35 min, mostly amd64 image builds).
make -C deploy aks-start

# Demo the saga end-to-end via the public LoadBalancer IP.
make -C deploy aks-smoke-test

# Tear down (~15-20 min).
make -C deploy aks-uninstall
cd infra/azure && terraform destroy -auto-approve
```

`aks-start` runs the full sequence: `terraform apply` → `aks-credentials` → service account → Postgres → bootstrap secrets → `docker buildx --platform linux/amd64` push to ACR → `helm install` → print the LoadBalancer IP.

**Auth model.** Developer → Azure via `az login` (OAuth device-code). Terraform → Azure via the developer's `az` context. AKS kubelet → ACR via Azure-managed identity (no `imagePullSecret`). Pods → Azure resources via Workload Identity (federated OIDC trust: K8s ServiceAccount token ⇄ Entra ID app, no client secrets stored anywhere). The cloud path uses zero long-lived secrets in the application's deployment surface.

## Demos

### Eventual consistency — basic transfer

```bash
ALICE=11111111-1111-1111-1111-111111111111
BOB=22222222-2222-2222-2222-222222222222

# Returns 202 + state=PENDING (outbox row written but not yet published)
RESPONSE=$(curl -s -i -X POST http://localhost:8080/transfers \
  -H 'Content-Type: application/json' \
  -d "{\"sourceAccountId\":\"$ALICE\",\"destinationAccountId\":\"$BOB\",\"amount\":100.00}")
ID=$(echo "$RESPONSE" | tail -1 | sed -E 's/.*"id":"([^"]+)".*/\1/')

# Within ~300ms the saga walks PENDING → DEBITED → COMPLETED
sleep 1 && curl -s http://localhost:8080/transfers/$ID

# Balances updated: Alice 900.00, Bob 600.00
curl -s http://localhost:8080/accounts/$ALICE
curl -s http://localhost:8080/accounts/$BOB
```

### Compensation — transfer to Carol triggers full reversal

```bash
CAROL=33333333-3333-3333-3333-333333333333

R=$(curl -s -X POST http://localhost:8080/transfers \
  -H 'Content-Type: application/json' \
  -d "{\"sourceAccountId\":\"$ALICE\",\"destinationAccountId\":\"$CAROL\",\"amount\":50.00}")
ID=$(echo "$R" | sed -E 's/.*"id":"([^"]+)".*/\1/')

# Within ~350ms: PENDING → DEBITED → REVERSED → FAILED.
sleep 1 && curl -s http://localhost:8080/transfers/$ID | python3 -m json.tool

# Alice unchanged — debited then refunded. Carol unchanged — credit rejected.
curl -s http://localhost:8080/accounts/$ALICE
curl -s http://localhost:8080/accounts/$CAROL
```

Carol's account is seeded with a `rejects_credits = true` flag — a deterministic failure injector for the saga compensation path.

### Idempotent POST — client retries are safe

```bash
KEY="my-attempt-$(date +%s)"

curl -s -X POST http://localhost:8080/transfers \
  -H "Content-Type: application/json" -H "Idempotency-Key: $KEY" \
  -d "{\"sourceAccountId\":\"$ALICE\",\"destinationAccountId\":\"$BOB\",\"amount\":5.00}"

# Same key → same transaction id returned; no duplicate transfer is created.
curl -s -X POST http://localhost:8080/transfers \
  -H "Content-Type: application/json" -H "Idempotency-Key: $KEY" \
  -d "{\"sourceAccountId\":\"$ALICE\",\"destinationAccountId\":\"$BOB\",\"amount\":5.00}"
```

### CQRS read model + notifications

```bash
# Statement — denormalised, populated by consuming saga events. Newest-first.
curl -s http://localhost:8080/accounts/$ALICE/statement | python3 -m json.tool

# Notifications for a specific transfer — terminal saga events only.
curl -s http://localhost:8080/notifications/$ID | python3 -m json.tool
```

### Inspect the database directly

```bash
docker exec postgres psql -U flexhub -d flexhub -c "TABLE accounts.accounts;"
docker exec postgres psql -U flexhub -d flexhub -c \
  "SELECT id, state, amount, reason FROM transactions.transactions ORDER BY created_at DESC LIMIT 5;"
docker exec postgres psql -U flexhub -d flexhub -c \
  "SELECT id, topic, sent_at IS NOT NULL AS sent FROM transactions.outbox_event ORDER BY created_at DESC LIMIT 5;"
```

### Watch messages in Kafka UI

Open **http://localhost:8090** → cluster `local` → Topics.

### Swagger UI

| Service | URL |
|---|---|
| account-service | http://localhost:8081/swagger-ui.html |
| transaction-service | http://localhost:8082/swagger-ui.html |

## Seed data

`account-service` seeds three demo accounts on first run:

| Account | UUID | Initial balance | Rejects credits? |
|---|---|---|---|
| Alice | `11111111-1111-1111-1111-111111111111` | 1000.00 | no |
| Bob | `22222222-2222-2222-2222-222222222222` | 500.00 | no |
| Carol | `33333333-3333-3333-3333-333333333333` | 0.00 | **yes** — sending to Carol triggers compensation |

Balances persist across restarts — Postgres, not in-memory.

## Tests

```bash
./gradlew test                                        # all tests
./gradlew :services:transaction-service:test          # one module
./gradlew test --tests "*OutboxRelayTest*"            # single class
```

Integration tests use Testcontainers — they spin up real Kafka and PostgreSQL containers and exercise the outbox, the saga, the consumer-side dedup table, and the idempotency dedup table end-to-end. The same tests run on every push in CI.

HTML report: `services/<service>/build/reports/tests/test/index.html`.

## CI/CD

Two GitHub Actions workflows:

- **[`ci.yml`](.github/workflows/ci.yml)** — on every push and PR: `./gradlew build` (compiles, runs all tests including Testcontainers, publishes JaCoCo coverage). Branch protection requires this to pass before merge to `main`.
- **[`image.yml`](.github/workflows/image.yml)** — gated on `ci.yml` success via `workflow_run`, with `head_sha` pinned so a rapid second push never publishes the wrong commit. Matrix-builds the four service images and pushes to GHCR tagged with both the commit SHA and `latest`.

The two-workflow split (rather than one big workflow) means red commits never reach the registry — image publishing is impossible without a green test gate.

## Infrastructure-as-Code

Terraform under [`infra/azure/`](infra/azure/) provisions:

- Resource group + AKS cluster (system-assigned identity, OIDC issuer, workload-identity webhook)
- Azure Container Registry, with `AcrPull` role-assignment to the kubelet identity
- Event Hubs namespace (Standard tier, Kafka-compatible) + six event hubs (one per saga topic)
- Key Vault (RBAC mode) with the Event Hubs connection string and DB password as secrets
- Workload-identity federated credentials linking K8s ServiceAccounts to Entra ID app identities
- Log Analytics workspace + Container Insights add-on on AKS

Run from `infra/azure/`:

```bash
terraform init
terraform apply        # ~12-15 min for the AKS cluster
terraform destroy      # full teardown
```
