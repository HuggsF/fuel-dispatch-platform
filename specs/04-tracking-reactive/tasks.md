# Phase 04 — Tracking reactive · Tasks

Rules: one task = one commit. Test first. Tick only when `./mvnw verify` is green.

- [x] **04.1** Tracking domain
  - Test: `OrderTrackingTest` — apply new, duplicate, out-of-order events; bounded processed ids.
  - Code: `OrderTracking`, `OrderStatusChanged`, `HistoryEntry`, `ApplyResult`.
  - _Req: TRK-1.1, TRK-1.2, TRK-1.3_
- [x] **04.2** Reactive Mongo adapter
  - Test: `MongoTrackingRepositoryIT` (Testcontainers MongoDB) — save/find/findByStatus with `StepVerifier`;
    stale version and duplicate insert fail with `ConcurrentTrackingUpdateException`.
  - Code: `TrackingRepository` port + `VersionedTracking` + `ConcurrentTrackingUpdateException`
    (the adapter implements the port, so it moves here from 04.3), document, mapper, adapter,
    index creation.
  - _Req: TRK-1.1, TRK-2.1, TRK-2.3_
- [x] **04.3** Application service
  - Test: `TrackingApplicationServiceTest` — notifies only on APPLIED; retries once on version conflict.
  - Code: remaining ports (`port.in`, `StatusChangeNotifier`) + service.
  - _Req: TRK-1.1, TRK-1.2, TRK-1.3_
- [x] **04.4** Kafka listener
  - Test: `OrderEventListenerIT` — contract examples from `contracts/examples` produce tracking docs;
    duplicate ignored; offset committed after save.
  - Test: `OrderEventListenerTest` — acknowledges only after the use case completes; no ack on error.
  - Code: consumer config (manual ack, group `tracking-service`), `OrderEventListener`, envelope DTO,
    `UseCaseConfig`; a basic `SinkStatusChangeNotifier` (publish/subscribe only) because the use case
    bean needs a notifier — backpressure and slow-subscriber behaviour stay in 04.6.
  - _Req: TRK-1.1, TRK-1.2, TRK-1.4, TRK-4.2_
- [x] **04.5** Query endpoints
  - Test: `TrackingControllerTest` — 200, 404 ProblemDetail, status filter streaming.
  - Code: `TrackingController`, error handler.
  - _Req: TRK-2.1, TRK-2.2, TRK-2.3_
- [x] **04.6** SSE live stream
  - Test: SSE receives changes after subscribe; `orderId` filter; heartbeat (virtual time); slow
    subscriber test.
  - Code: `SinkStatusChangeNotifier`, stream endpoint.
  - _Req: TRK-3.1, TRK-3.2, TRK-3.3, TRK-3.4_
- [x] **04.7** Architecture guards: ArchUnit + BlockHound.
  - _Req: TRK-4.1, TRK-NF-1_
- [x] **04.8** ADR 0005 reactive read side (0004 is the outbox); README demo: `curl -N localhost:8082/api/v1/tracking/stream`
  while creating orders on 8081.
- [ ] **04.9** Verify phase: `/spec-review 04`. **Milestone: publish the repository.**
  - First review (2026-09-30) failed on TRK-1.4; re-run after 04.10 and 04.11.
- [x] **04.10** Review fix: never commit an offset without saving
  - Test: `ConsumerFailureIT` — with MongoDB paused the offset is not committed and the event is
    saved after MongoDB resumes; an invalid payload is skipped without blocking the next event.
  - Code: `config.KafkaConsumerConfig` with a `DefaultErrorHandler` (unbounded exponential
    back-off, `InvalidOrderEventException` not retryable); design and ADR 0005 updated.
  - _Req: TRK-1.4_
- [x] **04.11** Review fix: bounded SSE stream for slow clients
  - Test: `TrackingStreamControllerTest` — with no demand, a client gets the change already in
    flight and the latest, not the ones in between; a client that stops reading for 10 minutes is
    not disconnected by a heartbeat overflow.
  - Code: `TrackingStreamController` — `Flux.merge` with prefetch 1, heartbeats
    `onBackpressureDrop()`; design and ADR 0005 wording corrected.
  - _Req: TRK-3.3, TRK-3.4_
