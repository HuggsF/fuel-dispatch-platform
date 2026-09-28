# ADR 0002 — Hexagonal architecture and domain events

- Status: accepted
- Date: 2026-09-28
- Requirements: DOM-3.1, DOM-3.2, DOM-4.1, DOM-4.2

## Context

`dispatch-service` owns the dispatch order lifecycle and its business rules (BR-1 to BR-6). Those
rules must be testable in milliseconds, without Spring, a database or Kafka, and they must survive
changes in the technology around them: the REST API (phase 02), the PostgreSQL schema (phase 02)
and the event pipeline to Kafka (phase 03).

Every state change must also produce a domain event that reaches Kafka and is never lost, even if
Kafka is down (BR-6). If the domain published to Kafka itself, it would depend on messaging, and
a state change could be committed while its event is lost (or the other way round).

Architecture rules that live only in a README erode quietly; the first convenient import from
`domain` into Spring is rarely noticed in review.

## Decision

**Hexagonal architecture (ports and adapters)** with the package layout of
`specs/steering/structure.md`:

- `domain` is plain Java 21: the `DispatchOrder` aggregate, self-validating value objects
  (records), domain events and exceptions. It depends on the JDK only. Time comes in as a
  `java.time.Clock` parameter, so tests use `Clock.fixed` instead of mocks.
- `application` holds driving ports (`port.in`), driven ports (`port.out`) and use-case services
  (`service`). Services may use only `@Service` and `@Transactional` from Spring.
- `adapter` implements ports with a technology (web, JPA, Kafka). Adapters reach the application
  only through ports and never depend on each other.
- `config` wires ports to adapters and may depend on everything.

The rules are **enforced by ArchUnit** in `HexagonalArchitectureTest`, which runs in
`./mvnw verify`. Deliberately broken fixture classes prove each rule rejects its violation while
the application and adapter layers are still empty.

**Domain events are registered in the aggregate and pulled by the application service:**

- Each successful state change registers an event in the aggregate; an invalid transition
  registers nothing (DOM-2.5).
- `DomainEvent` is a sealed interface (`OrderCreated`, `OrderApproved`, `OrderDispatched`,
  `OrderDelivered`, `OrderCancelled`), so mappers use exhaustive `switch` and the compiler flags a
  missing case when an event is added.
- Every event carries `eventId`, `orderId`, `occurredAt` and the data consumers need (DOM-3.2).
- The application service calls `pullDomainEvents()` (DOM-3.1) after the use case and hands the
  events to a driven port. In phase 03 that port writes them to an outbox table in the same
  database transaction as the order, and a relay publishes them to Kafka, which satisfies BR-6.

## Alternatives considered

| Option | Pros | Cons |
| --- | --- | --- |
| Hexagonal + ArchUnit (chosen) | Domain tested in isolation; adapters swappable; rules checked on every build | More packages and mapping code (JPA entity ↔ domain) than a layered CRUD app |
| Classic layered (controller → service → repository) with JPA entities as the domain | Less code; familiar | Business rules leak into services and annotations; domain tied to JPA; hard to test without Spring |
| Hexagonal without automated enforcement | Same structure, no extra test | Rules erode silently; violations found late, if at all |
| Aggregate publishes events directly (e.g. Spring `ApplicationEventPublisher` or Kafka) | Fewer moving parts | Domain depends on a framework; event and state change are not atomic, breaking BR-6 |
| Spring Data `@DomainEvents` / `AbstractAggregateRoot` | Built-in collection and publishing on `save` | Requires Spring types in the domain; publishing is tied to the repository call |
| Event sourcing | Full history by construction | Far more complexity than the problem needs; the read side (phase 04) already provides history |

## Consequences

- Positive: domain and rules are covered by fast, pure JUnit tests (JaCoCo gate on `domain` and
  `application`); frameworks can change without touching the domain; the outbox in phase 03
  plugs in behind a port without changing the aggregate.
- Negative: hand-written mappers between domain objects, JPA entities, DTOs and event payloads
  (no MapStruct, per `tech.md`); `DispatchOrder.rehydrate(...)` exists solely for persistence.
- Must do: every application service that changes an order must pull its events and pass them to
  the event port inside the same transaction; forgetting it would silently drop events, so phase
  02/03 use-case tests must assert it.
- Watch: new packages must follow the `structure.md` layout, otherwise ArchUnit's package patterns
  will not match them. `HexagonalArchitectureTest` covers `dispatch-service` only;
  `tracking-service` needs the same guard when its layers appear (phase 04).
