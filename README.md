# fuel-dispatch-platform

Event-driven platform for scheduling and tracking marine fuel deliveries at a port terminal —
two Java 21 / Spring Boot microservices that communicate only through Kafka.

> 🚧 Work in progress, built phase by phase with Spec-Driven Development. See [Roadmap](#roadmap).

[![CI](https://github.com/HuggsF/fuel-dispatch-platform/actions/workflows/ci.yml/badge.svg)](https://github.com/HuggsF/fuel-dispatch-platform/actions/workflows/ci.yml)
<!-- Badges: coverage (phase 06) -->

## Architecture

```mermaid
flowchart LR
    operator([Terminal operator]) -- REST --> api

    subgraph dispatch["dispatch-service · Spring MVC"]
        api[OrderController] --> useCase[OrderApplicationService]
        relay[OutboxRelay<br/>every 1 s]
    end

    subgraph postgres["PostgreSQL"]
        orders[(dispatch_order)]
        outbox[(outbox_event)]
    end

    useCase -- "one transaction:<br/>save order + its events" --> orders
    useCase --> outbox
    relay -- "lock oldest unpublished<br/>FOR UPDATE SKIP LOCKED" --> outbox
    relay -- "key = orderId<br/>acks=all" --> topic[[Kafka · dispatch.orders.v1]]
    relay -. "mark published_at<br/>after the ack" .-> outbox

    topic --> tracking["tracking-service · WebFlux<br/>idempotent consumer"]
    tracking --> mongo[(MongoDB)]
    tracking -- SSE --> panel([Status panel])
```

The two services never call each other: the only integration is the versioned event contract in
[`contracts/`](contracts/). Events are stored in the same transaction as the order and relayed
with at-least-once delivery, in order per order ([ADR 0004](docs/adr/0004-transactional-outbox-and-at-least-once-delivery.md)).

- **dispatch-service** — command side: order lifecycle and business rules, hexagonal architecture,
  transactional outbox.
- **tracking-service** — read side: idempotent Kafka consumer, reactive API and live status stream.

## What this project demonstrates

| Capability | Where |
| --- | --- |
| Java 21, Spring Boot 3, Maven multi-module | `pom.xml`, both services |
| Hexagonal architecture, DDD, OOP | `dispatch-service/.../domain`, `application`, `adapter` |
| REST APIs, OpenAPI, RFC 9457 errors | `dispatch-service/.../adapter/in/web` |
| PostgreSQL, JPA, Flyway | `dispatch-service/.../adapter/out/persistence`, `db/migration` |
| Kafka, outbox pattern, event contracts | `adapter/out/messaging`, `contracts/` |
| Reactive programming (WebFlux, Reactor), MongoDB | `tracking-service/` |
| Tests: JUnit 5, Mockito, Testcontainers, ArchUnit | `src/test` in each service |
| Resilience: retry, DLT, circuit breaker | phase 05 |
| Observability: Prometheus, Grafana | `observability/` |
| Docker, Kubernetes, CI/CD | `Dockerfile`s, `deploy/k8s/`, `.github/workflows/` |
| Spec-driven, documented decisions | `specs/`, `docs/adr/` |

## How to run

Prerequisites: JDK 21 and Docker (with Compose v2). Maven is not needed — use the wrapper.

```bash
./mvnw verify                                  # build + unit/integration tests + format check (Windows: mvnw.cmd verify)
./mvnw spotless:apply                          # fix formatting before committing

docker compose up -d                           # PostgreSQL, MongoDB, Kafka, Kafka UI
docker compose ps                              # every service should be "healthy"

docker compose --profile apps up -d --build    # infrastructure + both services
curl http://localhost:8081/actuator/health     # dispatch-service → {"status":"UP"}
curl http://localhost:8082/actuator/health     # tracking-service → {"status":"UP"}

docker compose --profile apps down             # stop everything (add -v to drop the data volumes)
```

### Try the dispatch API

With `docker compose --profile apps up -d --build` running, Swagger UI is at
<http://localhost:8081/swagger-ui.html> (OpenAPI JSON at `/v3/api-docs`).

```bash
# Create an order → 201 Created, Location: /api/v1/orders/{id}
curl -i -X POST http://localhost:8081/api/v1/orders \
  -H 'Content-Type: application/json' \
  -d '{"vesselName": "Nordic Star", "vesselImo": "9321483", "berth": "B-03",
       "fuelType": "VLSFO", "quantityM3": 850.5,
       "windowStart": "2026-10-01T08:00:00Z", "windowEnd": "2026-10-01T14:00:00Z"}'

ID=<id from the Location header>

curl http://localhost:8081/api/v1/orders/$ID                        # read it
curl -X POST http://localhost:8081/api/v1/orders/$ID/approve        # CREATED → APPROVED
curl -X POST http://localhost:8081/api/v1/orders/$ID/dispatch       # APPROVED → DISPATCHED
curl -X POST http://localhost:8081/api/v1/orders/$ID/deliver        # DISPATCHED → DELIVERED
curl -X POST http://localhost:8081/api/v1/orders/$ID/cancel \
  -H 'Content-Type: application/json' -d '{"reason": "Vessel delayed"}'   # only from CREATED or APPROVED

curl 'http://localhost:8081/api/v1/orders?status=APPROVED&page=0&size=20'   # newest first
```

Errors are RFC 9457 `application/problem+json` bodies with a `code`, e.g. delivering an order that
was only approved:

```json
{"type": "about:blank", "title": "Invalid order transition", "status": 409,
 "detail": "Cannot DELIVER an order in status APPROVED",
 "code": "INVALID_TRANSITION", "currentStatus": "APPROVED", "action": "DELIVER"}
```

Codes: `VALIDATION_FAILED` (400, with an `errors` list of `field`/`message`), `MALFORMED_REQUEST`
(400), `ORDER_NOT_FOUND` (404), `INVALID_TRANSITION` (409), `CONCURRENT_MODIFICATION` (409),
`INTERNAL_ERROR` (500).

| Component | Port |
| --- | --- |
| dispatch-service | 8081 |
| tracking-service | 8082 |
| PostgreSQL 16 | 5432 |
| MongoDB 7 | 27017 |
| Kafka (KRaft) | 9092 (from the host) · `kafka:19092` (inside the compose network) |
| Kafka UI | 8090 — browse topics and messages at <http://localhost:8090> |

Local credentials are development defaults in `docker-compose.yml`; override them by copying
`.env.example` to `.env`.

## Roadmap

- [x] 00 Foundation — Maven multi-module build, Docker Compose, CI, project board
- [x] 01 Dispatch domain — `DispatchOrder` aggregate, value objects, domain events, ArchUnit guard ([ADR 0002](docs/adr/0002-hexagonal-architecture-and-domain-events.md))
- [x] 02 Dispatch API — use cases, REST with RFC 9457 errors, PostgreSQL + Flyway, optimistic locking, OpenAPI
- [ ] 03 Dispatch events (Kafka + outbox)
- [ ] 04 Tracking service (reactive)
- [ ] 05 Resilience & observability
- [ ] 06 CI/CD & Kubernetes
- [ ] 07 Optional: AWS & Kotlin

Specs for every phase: [`specs/`](specs/README.md).

## Author

Hugo — backend engineer. Inspired by a real port fuel-logistics system I built in production.
<!-- LinkedIn link -->
