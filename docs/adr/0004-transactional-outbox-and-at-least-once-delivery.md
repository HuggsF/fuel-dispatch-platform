# ADR 0004 — Transactional outbox and at-least-once delivery

- Status: accepted
- Date: 2026-09-29
- Requirements: EVT-1.1, EVT-1.2, EVT-1.3, EVT-2.1–EVT-2.6, EVT-3.1, EVT-NF-1, BR-6

## Context

Every state change of a dispatch order must reach Kafka topic `dispatch.orders.v1`, and none may
be lost, even while Kafka is down (BR-6, EVT-1). The order lives in PostgreSQL; the event goes to
Kafka. The two systems cannot share a transaction, so "save the order, then send the event" has
two failure windows:

- the order commits and the process dies before sending: the event is lost;
- the event is sent and the database commit then fails: consumers see a change that never happened.

The command side must also keep accepting orders while Kafka is unavailable (EVT-1.3), several
instances of `dispatch-service` may run at once (EVT-2.5), and consumers rely on the events of one
order arriving in order (EVT-2.2).

## Decision

**Transactional outbox with a polling relay, at-least-once delivery, JSON Schema contract.**

1. **Outbox in the order's transaction.** The use case saves the order and hands its domain events
   to `DomainEventPublisher`. The adapter `OutboxDomainEventPublisher` writes one `outbox_event`
   row per event (id = eventId, JSON payload) and requires the caller's transaction
   (`Propagation.MANDATORY`). Order and events commit or roll back together.
2. **Polling relay.** `OutboxRelay` runs every second in its own transaction, locks up to 100
   unpublished rows oldest first, sends each to Kafka keyed by order id with `eventType` / `eventId`
   headers, waits for the acknowledgement (`acks=all`, idempotent producer) and sets
   `published_at`. On the first failure the batch stops; the rest waits for the next poll.
3. **Several relays.** The batch query uses `FOR UPDATE SKIP LOCKED`, so relays never take the
   same row, and takes only the **oldest unpublished row of each order** (head of line), so no
   relay can publish an order's second event while another relay still holds its first.
4. **At-least-once.** A crash between Kafka's acknowledgement and the commit of `published_at`
   re-sends the row. Consumers must deduplicate by `eventId` (phase 04).
5. **Contract.** Messages follow `contracts/dispatch-order-event.v1.schema.json` (JSON Schema
   2020-12), built by `DispatchOrderEventMapper` and checked against the schema in tests. Additive,
   optional fields keep v1; anything else creates v2 on a new topic.
6. **Housekeeping.** `OutboxCleanup` deletes rows published more than 7 days ago, daily.

## Alternatives considered

| Option | Pros | Cons |
| --- | --- | --- |
| Outbox + polling relay (chosen) | No event lost or invented; commands never wait for Kafka; plain SQL and Spring, easy to explain and test | Up to ~1 s latency; polling load on PostgreSQL; duplicates possible |
| Publish directly after commit | Lowest latency, no extra table | A crash between commit and send loses the event (violates BR-6); commands fail or block while Kafka is down |
| Change data capture (Debezium on the outbox or the orders table) | No polling; reads the WAL | Extra infrastructure (Kafka Connect, connectors) and operational knowledge for a two-service system |
| Kafka transactions / exactly-once | No duplicates inside Kafka | Does not cover the PostgreSQL write; still needs an outbox; more complex producer and consumer setup |
| Single active relay (leader election or advisory lock) | Ordering is trivial | Throughput and availability tied to one instance; `SKIP LOCKED` would be pointless |
| Avro + Schema Registry | Enforced compatibility, compact messages | Another service to run; heavier tooling for a portfolio project (possible future ADR) |

## Consequences

- Events are durable as soon as the order commits; Kafka outages only delay them.
- Consumers **must** be idempotent (dedupe by `eventId`) — a requirement for phase 04.
- The producer gives up on a send before the relay stops waiting for it (`delivery.timeout.ms`
  5 s < relay wait 6 s). Otherwise every failed poll during an outage leaves a buffered copy that
  is delivered when Kafka returns. With this setting an outage of any length costs at most one
  extra copy (a request already on the wire), measured by `OutboxKafkaOutageIT`.
- Latency between a command and its event is up to the relay interval (1 s by default) plus the
  send; acceptable for a status feed.
- Per-order ordering holds with any number of relays, at the price of at most one event per order
  per poll — irrelevant when an order changes status minutes apart.
- The outbox table is an operational signal: a growing count of unpublished rows means Kafka or
  the relay is unhealthy. Phase 05 should expose it as a metric and alert on it.
- Breaking contract changes need a v2 schema and topic published in parallel during migration.
