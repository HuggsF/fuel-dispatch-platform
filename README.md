# fuel-dispatch-platform

Event-driven platform for scheduling and tracking marine fuel deliveries at a port terminal —
two Java 21 / Spring Boot microservices that communicate only through Kafka.

> 🚧 Work in progress, built phase by phase with Spec-Driven Development. See [Roadmap](#roadmap).

[![CI](https://github.com/HuggsF/fuel-dispatch-platform/actions/workflows/ci.yml/badge.svg)](https://github.com/HuggsF/fuel-dispatch-platform/actions/workflows/ci.yml)
<!-- Badges: coverage (phase 06) -->

## Architecture

```
Operator ──REST──▶ dispatch-service ──(outbox)──▶ Kafka: dispatch.orders.v1 ──▶ tracking-service ──SSE──▶ Status panel
                   Spring MVC · PostgreSQL                                       WebFlux · MongoDB
```

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

docker compose up -d                           # PostgreSQL, MongoDB, Kafka
docker compose ps                              # every service should be "healthy"

docker compose --profile apps up -d --build    # infrastructure + both services
curl http://localhost:8081/actuator/health     # dispatch-service → {"status":"UP"}
curl http://localhost:8082/actuator/health     # tracking-service → {"status":"UP"}

docker compose --profile apps down             # stop everything (add -v to drop the data volumes)
```

| Component | Port |
| --- | --- |
| dispatch-service | 8081 |
| tracking-service | 8082 |
| PostgreSQL 16 | 5432 |
| MongoDB 7 | 27017 |
| Kafka (KRaft) | 9092 (from the host) · `kafka:19092` (inside the compose network) |

Local credentials are development defaults in `docker-compose.yml`; override them by copying
`.env.example` to `.env`.

## Roadmap

- [x] 00 Foundation — Maven multi-module build, Docker Compose, CI, project board
- [x] 01 Dispatch domain — `DispatchOrder` aggregate, value objects, domain events, ArchUnit guard ([ADR 0002](docs/adr/0002-hexagonal-architecture-and-domain-events.md))
- [ ] 02 Dispatch API
- [ ] 03 Dispatch events (Kafka + outbox)
- [ ] 04 Tracking service (reactive)
- [ ] 05 Resilience & observability
- [ ] 06 CI/CD & Kubernetes
- [ ] 07 Optional: AWS & Kotlin

Specs for every phase: [`specs/`](specs/README.md).

## Author

Hugo — backend engineer. Inspired by a real port fuel-logistics system I built in production.
<!-- LinkedIn link -->
