# Structure steering — fuel-dispatch-platform

## Repository layout

```
fuel-dispatch-platform/
├── CLAUDE.md                     # instructions for Claude Code
├── README.md
├── pom.xml                       # parent POM (created in phase 00)
├── docker-compose.yml            # created in phase 00
├── specs/                        # spec-driven source of truth
│   ├── steering/                 # product, tech, structure
│   ├── _templates/
│   └── NN-phase-name/            # requirements.md, design.md, tasks.md
├── contracts/                    # event JSON Schemas, versioned (phase 03)
├── dispatch-service/             # command side (Spring MVC + PostgreSQL)
├── tracking-service/             # read side (WebFlux + MongoDB)
├── deploy/k8s/                   # Kubernetes manifests (phase 06)
├── observability/                # Prometheus config, Grafana dashboards (phase 05)
├── docs/adr/                     # architecture decision records
├── docs/diagrams/
├── .claude/commands/             # spec workflow slash commands
└── .github/                      # PR template, CI (phase 00, hardened in 06), release workflow (phase 06)
```

## Base package

`com.fueldispatch.dispatch` and `com.fueldispatch.tracking`.

## Hexagonal layout (both services)

```
com.fueldispatch.<service>
├── domain/                  # entities, value objects, domain events, domain exceptions — plain Java
├── application/
│   ├── port/in/             # use-case interfaces (driving ports) + command/query records
│   ├── port/out/            # interfaces the application needs (repositories, publishers, clock)
│   └── service/             # use-case implementations; transactions live here
├── adapter/
│   ├── in/web/              # REST controllers, request/response DTOs, exception handler
│   ├── in/messaging/        # Kafka listeners (tracking-service)
│   ├── out/persistence/     # JPA or Mongo entities, Spring Data repositories, mappers
│   ├── out/messaging/       # outbox + Kafka producer (dispatch-service)
│   └── out/notification/    # in-memory SSE broadcaster (tracking-service)
└── config/                  # Spring @Configuration wiring ports to adapters
```

## Dependency rules (enforced by ArchUnit in phase 01/02)

| From | May depend on | Must NOT depend on |
| --- | --- | --- |
| `domain` | JDK only | `application`, `adapter`, `config`, Spring, JPA, Jackson, Kafka |
| `application` | `domain` | `adapter`, `config`, web/persistence/messaging frameworks |
| `adapter` | `application.port`, `domain` | other adapters |
| `config` | everything | — |

`application.service` may use `org.springframework.transaction.annotation.Transactional` and
`org.springframework.stereotype.Service` — the only framework allowance in that layer.

In `tracking-service` the whole `application` layer may also use Reactor's core types
(`reactor.core..`, `reactor.util..`: `Mono`, `Flux`, `Retry`), because its ports are reactive.
Reactor Netty and any web, persistence or messaging framework stay forbidden; the domain stays
JDK-only in both services.

## Naming

| Kind | Pattern | Example |
| --- | --- | --- |
| Driving port | `<Verb><Noun>UseCase` | `CreateOrderUseCase` |
| Command | `<Verb><Noun>Command` (record) | `CreateOrderCommand` |
| Driven port | `<Noun>Repository`, `<Noun>Publisher` | `OrderRepository`, `DomainEventPublisher` |
| Adapter | `<Tech><Port>Adapter` | `JpaOrderRepositoryAdapter` |
| DTO | `<Noun>Request` / `<Noun>Response` (record) | `CreateOrderRequest` |
| Test | `<ClassUnderTest>Test`, integration `...IT` | `DispatchOrderTest`, `OrderPersistenceIT` |

Unit tests run with Surefire (`*Test`), integration tests with Failsafe (`*IT`).
