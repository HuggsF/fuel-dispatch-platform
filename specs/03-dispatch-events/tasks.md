# Phase 03 — Dispatch events · Tasks

Rules: one task = one commit. Test first. Tick only when `./mvnw verify` is green.

- [x] **03.1** Event contract
  - Test: example JSON files for each event type validate against the schema; an invalid one fails.
  - Code: `contracts/dispatch-order-event.v1.schema.json`, `contracts/examples/*.json`.
  - _Req: EVT-3.1, EVT-3.2_
- [x] **03.2** Event mapper
  - Test: `DispatchOrderEventMapperTest` — each domain event maps to a schema-valid envelope.
  - Code: order data on every domain event (+ `DomainEvent` accessors, `DispatchOrderTest`
    updated); `DispatchOrderEventMapper`.
  - _Req: EVT-3.1, EVT-3.2, DOM-3.2_
- [x] **03.3** Outbox table and publisher port
  - Test: `OutboxTransactionIT` (commit → rows; rollback → no rows); service test verifies
    `DomainEventPublisher.publish` is called with pulled events.
  - Code: `V2__create_outbox_event.sql`, `DomainEventPublisher`, `OutboxDomainEventPublisher`,
    service change.
  - _Req: EVT-1.1, EVT-1.2_
- [ ] **03.4** Kafka producer config and topic
  - Code: spring-kafka dependency, producer properties, `KafkaTopicConfig`; Kafka UI in compose (optional).
  - _Req: EVT-2.2, EVT-3.3_
- [ ] **03.5** Outbox relay
  - Test: `OutboxRelayIT`, `OutboxRelayFailureTest`.
  - Code: `OutboxRelay` with key = orderId and headers.
  - _Req: EVT-2.1, EVT-2.2, EVT-2.3, EVT-2.4, EVT-1.3, EVT-3.3_
- [ ] **03.6** Concurrent relays
  - Test: `OutboxLockingIT`.
  - Code: native query with `FOR UPDATE SKIP LOCKED`.
  - _Req: EVT-2.5_
- [ ] **03.7** Cleanup job
  - Test: `OutboxCleanupIT`.
  - _Req: EVT-NF-1_
- [ ] **03.8** ADR 0004 outbox + at-least-once; architecture diagram in README.
- [ ] **03.9** Verify phase: `/spec-review 03`; demo: create an order and see the event in Kafka UI.
