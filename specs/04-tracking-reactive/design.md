# Phase 04 — Tracking reactive · Design

Status: approved

## Overview

```
Kafka dispatch.orders.v1 ─▶ @KafkaListener (listener thread) ─▶ ApplyOrderEventUseCase (Mono)
                                                                  ├─▶ ReactiveMongo: order_tracking
                                                                  └─▶ StatusChangeBroadcaster (Sinks.Many)
WebFlux: GET /tracking/{id}, GET /tracking?status=  ◀── ReactiveMongo
         GET /tracking/stream (SSE)                 ◀── StatusChangeBroadcaster
```

## Components

| Component | Package | Notes |
| --- | --- | --- |
| `OrderTracking` | `domain` | Aggregate: `orderId`, summary, `currentStatus`, `lastOccurredAt`, `history`, `processedEventIds`; method `apply(OrderStatusChanged)` returns `ApplyResult` (APPLIED / DUPLICATE / OUT_OF_ORDER_RECORDED) |
| `OrderStatusChanged`, `TrackingStatus`, `HistoryEntry` | `domain` | Own model; not shared with dispatch-service (TRK-4.2) |
| `ApplyOrderEventUseCase`, `GetTrackingQuery`, `ListTrackingsQuery`, `StreamStatusChangesQuery` | `application.port.in` | `Mono<ApplyResult> apply(OrderStatusChanged)`, `Mono<OrderTracking> get(UUID)`, `Flux<OrderTracking> listByStatus(TrackingStatus)`, `Flux<OrderStatusChanged> streamChanges(Optional<UUID>)` |
| `TrackingNotFoundException` | `application` | Error of `get` for an unknown order (TRK-2.2), like dispatch's `OrderNotFoundException` |
| `TrackingRepository`, `VersionedTracking`, `ConcurrentTrackingUpdateException` | `application.port.out` | Reactive repository port; see "Optimistic locking" below |
| `StatusChangeNotifier` | `application.port.out` | `void publish(OrderStatusChanged)` (never blocks, never fails the caller), `Flux<OrderStatusChanged> changes()`; the SSE payload is the applied `OrderStatusChanged` |
| `TrackingApplicationService` | `application.service` | Loads or creates, applies, saves (skipped for DUPLICATE), then notifies only when APPLIED and only after the save; retries once on `ConcurrentTrackingUpdateException` from a fresh read; other errors are not retried. No transactions (one document write). Plain class, bean created in `config` like dispatch's `UseCaseConfig` |
| `OrderEventListener` | `adapter.in.messaging` | Spring Kafka `@KafkaListener`; deserializes the v1 envelope; calls the use case and `block(Duration.ofSeconds(10))` — allowed because it runs on the Kafka listener thread, not on Netty; manual ack after save |
| `TrackingController` | `adapter.in.web` | Annotated WebFlux controller |
| `MongoTrackingRepositoryAdapter`, `OrderTrackingDocument` | `adapter.out.persistence` | Collection `order_tracking`, `_id` = orderId, `@Version`; index on `currentStatus` |
| `SinkStatusChangeNotifier` | `adapter.out.notification` | `Sinks.many().multicast().onBackpressureBuffer()`; per subscriber `onBackpressureLatest()` |

Idempotency: `processedEventIds` keeps the last 50 event ids per order (bounded); a duplicate
returns `DUPLICATE`. Optimistic locking (`@Version`) + retry once handles concurrent writes.

## Optimistic locking

Reactive MongoDB has no transaction-scoped managed entity, so the ADR 0003 approach (the adapter
reloads the row it already holds) would read the *current* version and never see a conflict. The
version read by `findById` must travel to `save`. It travels next to the aggregate, not inside
it, so the domain stays free of persistence concerns as in ADR 0003:

```java
// application.port.out
record VersionedTracking(OrderTracking tracking, Long version) {}  // version null = never saved

interface TrackingRepository {
    Mono<VersionedTracking> findById(UUID orderId);            // empty when unknown
    Flux<OrderTracking> findByStatus(TrackingStatus status);    // read side, no version needed
    Mono<VersionedTracking> save(VersionedTracking tracking);   // returns the new version
}
```

- `save` with `version == null` inserts; with a version it updates only if the stored version
  still matches (`@Version` on `OrderTrackingDocument`).
- A stale version, or an insert racing another insert of the same order, errors with
  `ConcurrentTrackingUpdateException` (application.port.out). The adapter translates Spring's
  `OptimisticLockingFailureException` / `DuplicateKeyException` so that `application` never sees
  Spring Data types (structure.md). The service retries once on it (04.3).
- The version is opaque to the application: it only passes it back.

## Consuming events

- `OrderEventListener` reads the value as a `String` (`StringDeserializer`, no Spring type
  headers) and parses it into `OrderEventMessage`, a record written from the JSON Schema only
  (TRK-4.2), `@JsonIgnoreProperties(ignoreUnknown = true)` as the contract requires of consumers.
- Consumer: group `tracking-service`, `auto-offset-reset: earliest` (a new group rebuilds the view
  from the topic), `enable-auto-commit: false`, `ack-mode: manual_immediate`. The listener blocks
  on the use case (max 10 s) and calls `acknowledge()` only after it completes (TRK-1.4);
  duplicates are acknowledged too.
- A payload that is not valid JSON or misses a required field fails with
  `InvalidOrderEventException`; any failure is rethrown without ack. Until phase 05 adds the DLT,
  Spring Kafka's default error handler retries it 9 times and then logs and skips it.
- The topic is declared by dispatch-service; tracking-service only subscribes.
- `SinkStatusChangeNotifier` exists from 04.4 in a basic form (`multicast().directBestEffort()`,
  `tryEmitNext`) so that the use case bean can be wired. Note for 04.6:
  `multicast().onBackpressureBuffer()` would buffer changes while nobody is subscribed and replay
  them to the first subscriber, which breaks "applied after the connection" (TRK-3.1).

## Persistence notes

- `order_tracking` document: `_id` and event ids stored as UUID strings (readable in any client),
  `summary` and `history` embedded, `quantityM3` as `Decimal128` (exact), `currentStatus` indexed
  (`@Indexed` + `spring.data.mongodb.auto-index-creation: true`).
- Instants are stored as native MongoDB dates, which have **millisecond** precision; the contract
  allows microseconds. Two events of one order within the same millisecond therefore compare as
  "same instant" after a reload, and the tie rule (applied) decides. Accepted: events of one order
  are seconds apart in practice, and native dates keep the collection queryable by time.

## Domain model

All types in `com.fueldispatch.tracking.domain`, derived from the v1 contract
(`contracts/dispatch-order-event.v1.schema.json`), owned by tracking-service (TRK-4.2).

| Type | Shape |
| --- | --- |
| `TrackingStatus` | enum `CREATED, APPROVED, DISPATCHED, DELIVERED, CANCELLED` (= `data.status`) |
| `OrderSummary` | record `vesselName, vesselImo, berth, fuelType (String), quantityM3 (BigDecimal)` — plain values, the read side trusts the producer-validated contract |
| `OrderStatusChanged` | record `eventId, orderId (UUID), occurredAt, status, summary, reason` — all but `reason` required (`DomainValidationException`); `reason` only on `CANCELLED` |
| `HistoryEntry` | record `eventId, status, occurredAt, reason` |
| `ApplyResult` | enum `APPLIED, DUPLICATE, OUT_OF_ORDER_RECORDED` |
| `OrderTracking` | `forOrder(orderId)` (no events yet: status/summary/lastOccurredAt `null`), `rehydrate(...)`, `apply(event)` |

`OrderTracking.apply` rules, in order:

1. Event of another order → `DomainValidationException`.
2. `eventId` in `processedEventIds` → `DUPLICATE`, nothing changes (TRK-1.2).
3. Otherwise remember the id (FIFO, max 50) and insert a `HistoryEntry` keeping history sorted by
   `occurredAt` (stable for equal instants).
4. `occurredAt` strictly before `lastOccurredAt` → `OUT_OF_ORDER_RECORDED`; status, summary and
   `lastOccurredAt` unchanged (TRK-1.3). An equal instant is not "older" and is applied.
5. Else → `APPLIED`: status, summary and `lastOccurredAt` take the event values (TRK-1.1).

No version field in the domain: optimistic locking stays in the persistence adapter (ADR 0003).

## Endpoints

| Method | Path | Produces | Notes |
| --- | --- | --- | --- |
| GET | `/api/v1/tracking/{orderId}` | JSON | 404 `TRACKING_NOT_FOUND` |
| GET | `/api/v1/tracking?status=APPROVED` | `application/x-ndjson` or JSON array | `Flux` |
| GET | `/api/v1/tracking/stream?orderId=` | `text/event-stream` | SSE `event: status-changed`; heartbeat via `Flux.interval(15s)` merged as comment |

Response bodies (`adapter.in.web.dto`):

- `TrackingResponse`: `orderId, currentStatus, lastOccurredAt, vesselName, vesselImo, berth,
  fuelType, quantityM3, history[] {eventId, status, occurredAt, reason}`, with history sorted by
  `occurredAt`. `processedEventIds` is internal and never exposed.
- `TrackingSummaryResponse`: the same fields without `history`. `status` is required.

Errors (`ApiExceptionHandler`, WebFlux `ResponseEntityExceptionHandler`), same shape as
dispatch-service:

| Case | Status | `code` |
| --- | --- | --- |
| No tracking for the order (TRK-2.2) | 404 | `TRACKING_NOT_FOUND` |
| Bad UUID, unknown or missing `status` | 400 | `VALIDATION_FAILED` + `errors[] {field, message}` |
| Other WebFlux errors (unknown route, 405, 406…) | as is | HTTP status name |
| Anything else (logged, cause not exposed) | 500 | `INTERNAL_ERROR` |

## Key decisions

| Decision | Alternatives | Why | ADR |
| --- | --- | --- | --- |
| Spring Kafka listener + `block()` on listener thread | reactor-kafka | reactor-kafka is no longer actively developed; offset handling with Spring Kafka is simpler and well known. Good interview talking point: where reactive does and does not pay off | 0004 |
| In-memory `Sinks` for SSE | MongoDB change streams | Change streams need a replica set; Sinks is enough for one instance. Multi-instance fan-out noted as future work | 0004 |
| MongoDB for the read side | PostgreSQL | Document per order matches the read pattern (status + nested history) | 0004 |

## Testing strategy

| Requirement IDs | Test | Type |
| --- | --- | --- |
| TRK-1.1–1.3 | `OrderTrackingTest` | unit |
| TRK-1.x | `TrackingApplicationServiceTest` with `StepVerifier` + Mockito | unit |
| TRK-2.x, 3.x | `TrackingControllerTest` (`@WebFluxTest`, `WebTestClient`, `StepVerifier` on SSE) | slice |
| TRK-1.4, end to end | `OrderEventListenerIT` — Testcontainers Kafka + MongoDB; publish contract examples; duplicate ignored | IT |
| TRK-3.4 | `SinkStatusChangeNotifierTest` — slow subscriber does not block a fast one | unit |
| TRK-4.1 | `HexagonalArchitectureTest` | unit |
| TRK-NF-1 | BlockHound installed in WebFlux tests | slice |
