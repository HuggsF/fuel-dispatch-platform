# Phase 03 — Dispatch events · Design

Status: approved

## Overview

```
use case (tx) ──save order──▶ orders table
              └─pullDomainEvents─▶ OutboxWriter ──▶ outbox_event table   (same tx)

OutboxRelay (@Scheduled, every 1s) ──select … FOR UPDATE SKIP LOCKED──▶ KafkaTemplate.send(key=orderId)
                                   ◀──ack── mark published_at
```

## Components

| Component | Package | Notes |
| --- | --- | --- |
| `DomainEventPublisher` | `application.port.out` | `publish(List<DomainEvent>)`; called by the service after `save` |
| `OutboxDomainEventPublisher` | `adapter.out.messaging` | Implements the port by writing outbox rows (joins the caller's tx: `Propagation.MANDATORY`) |
| `OutboxEventJpaEntity`, `SpringDataOutboxRepository` | `adapter.out.messaging.outbox` | Native query with `FOR UPDATE SKIP LOCKED` |
| `DispatchOrderEventMapper` | `adapter.out.messaging` | Exhaustive `switch` over the sealed `DomainEvent` → JSON envelope |
| `OutboxRelay` | `adapter.out.messaging` | `@Scheduled(fixedDelayString="${outbox.relay.interval:1000}")`; own transaction per batch; `send(...).get(5, SECONDS)` |
| `OutboxCleanup` | `adapter.out.messaging` | Daily; deletes published rows older than 7 days |
| `KafkaTopicConfig` | `config` | `NewTopic dispatch.orders.v1` (3 partitions, replication 1 locally) |

Producer settings: `acks=all`, `enable.idempotence=true`, JSON value (Jackson `ObjectMapper`,
not Spring's type headers), `StringSerializer` key.

## Schema — `V2__create_outbox_event.sql`

| Column | Type |
| --- | --- |
| `id` | `uuid` PK (= eventId) |
| `aggregate_id` | `uuid` not null |
| `event_type` | `varchar(50)` not null |
| `payload` | `jsonb` not null |
| `occurred_at` | `timestamptz` not null |
| `published_at` | `timestamptz` null |

Partial index: `create index … on outbox_event (occurred_at) where published_at is null`.

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

Versioning rule: additive, optional fields keep `v1`; anything else creates `v2` and a new topic
`dispatch.orders.v2` published in parallel during migration.

## Key decisions

| Decision | Alternatives | Why | ADR |
| --- | --- | --- | --- |
| Outbox + polling relay | Publish directly after commit; Debezium CDC | Direct publish loses events on crash; CDC adds infrastructure. Polling is simple and explainable | 0003 |
| At-least-once + idempotent consumers | Exactly-once transactions | Simpler; consumers dedupe by `eventId` (phase 04) | 0003 |
| JSON Schema, no Schema Registry | Avro + Registry | Lower setup cost for a portfolio; noted as future work | 0003 |

## Testing strategy

| Requirement IDs | Test | Type |
| --- | --- | --- |
| EVT-1.1, 1.2 | `OutboxTransactionIT` — commit writes rows; forced exception writes none | IT (Postgres) |
| EVT-3.x | `DispatchOrderEventMapperTest` + validation against the JSON Schema (networknt json-schema-validator) | unit |
| EVT-2.1–2.3 | `OutboxRelayIT` — Testcontainers Postgres + Kafka; consume from topic; row marked published | IT |
| EVT-2.4, 1.3 | `OutboxRelayFailureTest` — `KafkaTemplate` mocked to fail; row stays unpublished | unit |
| EVT-2.5 | `OutboxLockingIT` — two relays in parallel publish each row once | IT |
| EVT-NF-1 | `OutboxCleanupIT` | IT |
