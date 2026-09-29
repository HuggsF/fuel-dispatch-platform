# Phase 03 — Dispatch events · Design

Status: approved

## Overview

```
use case (tx) ──save order──▶ dispatch_order
              └─pullDomainEvents─▶ OutboxDomainEventPublisher ──▶ outbox_event   (same tx)

OutboxRelay (@Scheduled, every 1s, own tx)
   ──lockNextBatch: oldest unpublished row of each order, FOR UPDATE SKIP LOCKED, limit 100──▶
   KafkaTemplate.send(key=orderId, headers eventType/eventId).get(6 s)
   ◀──ack── mark published_at        (first failure stops the batch)

OutboxCleanup (daily) ──delete rows published more than 7 days ago
```

## Components

| Component | Package | Notes |
| --- | --- | --- |
| `DomainEventPublisher` | `application.port.out` | `publish(List<DomainEvent>)`; called by the service after `save` |
| `OutboxDomainEventPublisher` | `adapter.out.messaging` | Implements the port by writing outbox rows (joins the caller's tx: `Propagation.MANDATORY`) |
| `OutboxEventJpaEntity`, `SpringDataOutboxRepository` | `adapter.out.messaging.outbox` | Native query with `FOR UPDATE SKIP LOCKED` that takes only the oldest unpublished row of each order (head of line), so parallel relays never reorder an order's events (EVT-2.6). The entity implements `Persistable` (id = domain eventId), so new rows are inserted without a prior select |
| `DispatchOrderEventMapper` | `adapter.out.messaging` | Exhaustive `switch` over the sealed `DomainEvent` → JSON envelope. Common order data comes from `DomainEvent` accessors (`status`, `vessel`, `berth`, `fuelType`, `quantity`); the switch picks `eventType` and `reason` |
| `OutboxRelay` | `adapter.out.messaging` | `@Scheduled(fixedDelayString="${outbox.relay.interval:1000}")`; own transaction per batch; `send(...).get(6, SECONDS)`, longer than the producer's `delivery.timeout.ms` (5 s). Stops the batch at the first failure (see below). Off when `outbox.relay.enabled=false` (tests that inspect unpublished rows) |
| `OutboxCleanup` | `adapter.out.messaging` | Daily (`outbox.cleanup.cron`, default 03:00 UTC); deletes rows whose `published_at` is more than `outbox.cleanup.retention` (default `P7D`) ago. Retention counts from publication, so an event relayed late after a Kafka outage is kept for the full period; unpublished rows are never deleted |
| `KafkaTopicConfig` | `config` | `NewTopic dispatch.orders.v1` (3 partitions, replication 1 locally) |

The service saves the order, then publishes `order.pullDomainEvents()` — pulled from the
instance it changed, because `OrderRepository.save` returns a rehydrated copy without events.

Every event carries the order snapshot at the time of the change (DOM-3.2), so the mapper needs
nothing but the event and `DomainEventPublisher.publish(List<DomainEvent>)` stays unchanged.
The mapper builds the envelope as a Jackson `ObjectNode` by hand (`toJson(event)`), so the wire
format does not depend on `ObjectMapper` settings; `eventType(event)` gives the name used for the
outbox row and the Kafka header.

Relay failure handling: rows are sent one at a time, oldest first. On the first failure the
batch stops; rows already acknowledged are marked published when the batch commits, the failed
row and every later one wait for the next poll. Skipping only the failed row would let a later
event of the same order overtake it (EVT-2.2). `max.block.ms=5000` makes `send` fail fast when
Kafka is down, so the relay never holds its transaction for the producer's default 60 s.

The producer also gives up on a send before the relay stops waiting for it
(`request.timeout.ms=4000`, `delivery.timeout.ms=5000` < the relay's 6 s). With the defaults
(120 s) a send the relay counted as failed stayed buffered in the producer and was delivered when
Kafka came back, next to the relay's retry: one duplicate per failed poll during an outage (the
phase review measured 4 copies after 3 failed polls). Now an outage costs at most one extra copy —
a request already on the wire when the broker stopped answering — whatever its length
(`OutboxKafkaOutageIT`).

Producer settings: `acks=all`, `enable.idempotence=true`, `StringSerializer` for key and value.
The value is the outbox `payload` — JSON already built by `DispatchOrderEventMapper`, stored and
sent verbatim — so no Spring `JsonSerializer` type headers reach the contract. The topic name is
`DispatchOrderEventMapper.TOPIC` (tied to the contract version); partitions and replicas come
from `dispatch.kafka.topic.partitions` / `.replicas` (defaults 3 / 1). Kafka UI
(`kafbat/kafka-ui`) runs in compose on port 8090.

## Schema — `V2__create_outbox_event.sql`

| Column | Type |
| --- | --- |
| `id` | `uuid` PK (= eventId) |
| `aggregate_id` | `uuid` not null |
| `event_type` | `varchar(50)` not null |
| `payload` | `jsonb` not null — `json` since V4 |
| `occurred_at` | `timestamptz` not null |
| `published_at` | `timestamptz` null |

Partial index: `create index … on outbox_event (occurred_at) where published_at is null`.

`V4__keep_outbox_payload_verbatim.sql` turns `payload` into `json`: `jsonb` normalises the text
(key order, whitespace), so Kafka received a rewritten JSON instead of the mapper's. `json` still
rejects invalid JSON but keeps the text as written. The entity binds the payload as text with a
`?::json` cast (`@ColumnTransformer`), because Hibernate's JSON type would bind it as `jsonb`.

`V3__index_outbox_head_of_line.sql`: partial index on `(aggregate_id, occurred_at) where
published_at is null`, used by the head-of-line `not exists` of the relay query.

## Contract — `contracts/dispatch-order-event.v1.schema.json`

JSON Schema draft 2020-12. Example:

```json
{
  "eventId": "7b1c…",
  "eventType": "OrderApproved",
  "eventVersion": 1,
  "occurredAt": "2026-10-05T14:03:22Z",
  "orderId": "1f0e…",
  "data": {
    "status": "APPROVED",
    "vesselName": "MV Atlantic Star",
    "vesselImo": "9321483",
    "berth": "B-03",
    "fuelType": "VLSFO",
    "quantityM3": 850.000,
    "reason": null
  }
}
```

Rules the schema enforces (beyond the required fields of EVT-3.2):

- `data.status` matches `eventType` (`OrderApproved` → `APPROVED`, …).
- `reason` is a non-blank string of at most 500 characters on `OrderCancelled`; null or absent
  on every other event type.
- `vesselImo` is 7 digits; `fuelType` is `MGO`/`VLSFO`/`HFO`; `quantityM3` is in (0, 10000] with
  at most 3 decimals — the same rules as the domain (BR-1, BR-3, BR-5).
- `eventId`/`orderId` are UUIDs; `occurredAt` is an RFC 3339 date-time in UTC (`Z`).
- Unknown fields are allowed (no `additionalProperties: false`), so additive changes stay `v1`.

Tests load the schema from `../contracts/` (module working directory) with networknt
json-schema-validator 2.x — the last line on Jackson 2 — with format assertions enabled.

Versioning rule: additive, optional fields keep `v1`; anything else creates `v2` and a new topic
`dispatch.orders.v2` published in parallel during migration.

## Key decisions

| Decision | Alternatives | Why | ADR |
| --- | --- | --- | --- |
| Outbox + polling relay | Publish directly after commit; Debezium CDC | Direct publish loses events on crash; CDC adds infrastructure. Polling is simple and explainable | 0004 |
| At-least-once + idempotent consumers | Exactly-once transactions | Simpler; consumers dedupe by `eventId` (phase 04) | 0004 |
| JSON Schema, no Schema Registry | Avro + Registry | Lower setup cost for a portfolio; noted as future work | 0004 |

## Testing strategy

| Requirement IDs | Test | Type |
| --- | --- | --- |
| EVT-3.1, 3.2 | `DispatchOrderEventContractTest` — examples of every event type validate; 26 broken variants fail | unit |
| EVT-3.1, 3.2, DOM-3.2 | `DispatchOrderEventMapperTest` — every domain event maps to a schema-valid envelope; `DispatchOrderTest` — every event carries the order data | unit |
| EVT-1.1, 1.2 | `OutboxTransactionIT` — commit writes rows (payload verbatim, schema-valid); rollback writes none; `publish` outside a transaction is rejected. `OrderApplicationServiceTest` — events published after `save` | IT (Postgres) / unit |
| EVT-2.2, 3.3, 1.3 | `KafkaTopicConfigTest`, `KafkaProducerConfigIT` — topic with 3 partitions, string key/value, `acks=all`, idempotence, `max.block.ms` | unit / IT (Kafka) |
| EVT-2.1–2.3, 3.1, 3.3 | `OutboxRelayIT` — scheduled relay, consume from topic: in order, same partition, headers, verbatim schema-valid value; rows marked published | IT (Postgres + Kafka) |
| EVT-2.1–2.4, 1.3, 3.3 | `OutboxRelayFailureTest` — `KafkaTemplate` mocked: ack marks published; failure stops the batch; retry on the next poll; Kafka unavailable | unit |
| EVT-1.3, 2.4 | `OutboxKafkaOutageIT` — broker paused: commands still succeed, event waits in the outbox, published once Kafka is back with at most one extra copy; producer gives up before the relay | IT (Postgres + Kafka) |
| EVT-2.1, 2.5, 2.6 | `OutboxLockingIT` — batch limit of 100 oldest first; a locked batch is skipped and an order's next event waits behind it; two relays in parallel publish each event once and in order | IT (Postgres + Kafka) |
| EVT-NF-1 | `OutboxCleanupIT` — only rows published more than 7 days ago are deleted | IT (Postgres) |
