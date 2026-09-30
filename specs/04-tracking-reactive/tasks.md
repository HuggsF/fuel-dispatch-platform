# Phase 04 — Tracking reactive · Tasks

Rules: one task = one commit. Test first. Tick only when `./mvnw verify` is green.

- [x] **04.1** Tracking domain
  - Test: `OrderTrackingTest` — apply new, duplicate, out-of-order events; bounded processed ids.
  - Code: `OrderTracking`, `OrderStatusChanged`, `HistoryEntry`, `ApplyResult`.
  - _Req: TRK-1.1, TRK-1.2, TRK-1.3_
- [ ] **04.2** Reactive Mongo adapter
  - Test: `MongoTrackingRepositoryIT` (Testcontainers MongoDB) — save/find/findByStatus with `StepVerifier`.
  - Code: document, mapper, adapter, index creation.
  - _Req: TRK-1.1, TRK-2.1, TRK-2.3_
- [ ] **04.3** Application service
  - Test: `TrackingApplicationServiceTest` — notifies only on APPLIED; retries once on version conflict.
  - Code: ports + service.
  - _Req: TRK-1.1, TRK-1.2, TRK-1.3_
- [ ] **04.4** Kafka listener
  - Test: `OrderEventListenerIT` — contract examples from `contracts/examples` produce tracking docs;
    duplicate ignored; offset committed after save.
  - Code: consumer config (manual ack, group `tracking-service`), `OrderEventListener`, envelope DTO.
  - _Req: TRK-1.1, TRK-1.2, TRK-1.4, TRK-4.2_
- [ ] **04.5** Query endpoints
  - Test: `TrackingControllerTest` — 200, 404 ProblemDetail, status filter streaming.
  - Code: `TrackingController`, error handler.
  - _Req: TRK-2.1, TRK-2.2, TRK-2.3_
- [ ] **04.6** SSE live stream
  - Test: SSE receives changes after subscribe; `orderId` filter; heartbeat (virtual time); slow
    subscriber test.
  - Code: `SinkStatusChangeNotifier`, stream endpoint.
  - _Req: TRK-3.1, TRK-3.2, TRK-3.3, TRK-3.4_
- [ ] **04.7** Architecture guards: ArchUnit + BlockHound.
  - _Req: TRK-4.1, TRK-NF-1_
- [ ] **04.8** ADR 0004 reactive read side; README demo: `curl -N localhost:8082/api/v1/tracking/stream`
  while creating orders on 8081.
- [ ] **04.9** Verify phase: `/spec-review 04`. **Milestone: publish the repository.**
