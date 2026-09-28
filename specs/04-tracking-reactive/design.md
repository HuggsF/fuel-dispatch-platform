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
| `ApplyOrderEventUseCase`, `GetTrackingQuery`, `StreamStatusChangesQuery` | `application.port.in` | Return `Mono` / `Flux` |
| `TrackingRepository`, `StatusChangeNotifier` | `application.port.out` | Reactive signatures |
| `TrackingApplicationService` | `application.service` | Loads or creates, applies, saves, notifies when APPLIED |
| `OrderEventListener` | `adapter.in.messaging` | Spring Kafka `@KafkaListener`; deserializes the v1 envelope; calls the use case and `block(Duration.ofSeconds(10))` — allowed because it runs on the Kafka listener thread, not on Netty; manual ack after save |
| `TrackingController` | `adapter.in.web` | Annotated WebFlux controller |
| `MongoTrackingRepositoryAdapter`, `OrderTrackingDocument` | `adapter.out.persistence` | Collection `order_tracking`, `_id` = orderId, `@Version`; index on `currentStatus` |
| `SinkStatusChangeNotifier` | `adapter.out.notification` | `Sinks.many().multicast().onBackpressureBuffer()`; per subscriber `onBackpressureLatest()` |

Idempotency: `processedEventIds` keeps the last 50 event ids per order (bounded); a duplicate
returns `DUPLICATE`. Optimistic locking (`@Version`) + retry once handles concurrent writes.

## Endpoints

| Method | Path | Produces | Notes |
| --- | --- | --- | --- |
| GET | `/api/v1/tracking/{orderId}` | JSON | 404 `TRACKING_NOT_FOUND` |
| GET | `/api/v1/tracking?status=APPROVED` | `application/x-ndjson` or JSON array | `Flux` |
| GET | `/api/v1/tracking/stream?orderId=` | `text/event-stream` | SSE `event: status-changed`; heartbeat via `Flux.interval(15s)` merged as comment |

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
