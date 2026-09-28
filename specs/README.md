# Specs

This project is built with **Spec-Driven Development**: every line of production code traces back
to an acceptance criterion written here first.

## Flow per phase

```
requirements.md ──approve──▶ design.md ──approve──▶ tasks.md ──▶ implement task by task ──▶ review
     (what)                    (how)                  (steps)        (/spec-implement)     (/spec-review)
```

## Phases

| # | Phase | Requirement prefix | Demonstrates | Spec files |
| --- | --- | --- | --- | --- |
| 00 | [Foundation](00-foundation/) | FND | Maven, Docker, CI skeleton, Scrum board | req · design · tasks |
| 01 | [Dispatch domain](01-dispatch-domain/) | DOM | OOP, DDD, hexagonal core, unit tests | req · design · tasks |
| 02 | [Dispatch API](02-dispatch-api/) | API | Spring Boot, REST, JPA, PostgreSQL, Mockito, Testcontainers | req · design · tasks |
| 03 | [Dispatch events](03-dispatch-events/) | EVT | Kafka, outbox pattern, event contracts | req · design · tasks |
| 04 | [Tracking reactive](04-tracking-reactive/) | TRK | WebFlux, reactive MongoDB, idempotent consumer, SSE | req · design · tasks |
| 05 | [Resilience & observability](05-resilience-observability/) | RES / OBS | Resilience4j, DLT, Prometheus, Grafana | req · design · tasks |
| 06 | [Delivery](06-delivery/) | DEL | CI/CD, Docker images, Kubernetes, ADRs, README | req · design · tasks |
| 07 | [Optional: cloud & Kotlin](07-optional-cloud-kotlin/) | OPT | AWS deploy, Kotlin | req only |

Phases 00–04 cover the core of the job description. Publish the repository after phase 04 at the
latest, with the roadmap visible in the README.

## Traceability

Requirement IDs (`API-2.3`) appear in: `requirements.md` → `design.md` traceability table →
`tasks.md` (`_Req:_`) → test `@DisplayName` or comment → commit message / PR description.
