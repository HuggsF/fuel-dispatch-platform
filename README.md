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

_Filled in during phase 00._

## Roadmap

- [ ] 00 Foundation
- [ ] 01 Dispatch domain
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
