# Phase 03 — Dispatch events · Requirements

Status: approved
Depends on: phase 02

## Goal

Publish every order state change to Kafka reliably, using the transactional outbox pattern and a
versioned event contract. Demonstrates messaging, event-driven architecture and consistency in
distributed systems.

## Requirements

### EVT-1 Transactional outbox

**User story:** As the control room, I want every state change to reach the event stream, so that
no status update is ever lost. (BR-6)

- **EVT-1.1** WHEN a use case changes an order THE SYSTEM SHALL store the resulting domain events
  in the `outbox_event` table in the **same database transaction** as the order.
- **EVT-1.2** IF the transaction rolls back THEN THE SYSTEM SHALL store no outbox rows.
- **EVT-1.3** WHILE Kafka is unavailable THE SYSTEM SHALL keep accepting commands and keep the
  events in the outbox.

### EVT-2 Relay to Kafka

- **EVT-2.1** THE SYSTEM SHALL poll unpublished outbox rows (oldest first, batch of up to 100) and
  publish them to topic `dispatch.orders.v1`.
- **EVT-2.2** THE SYSTEM SHALL use the order id as the message key, so events of one order keep
  their order within a partition.
- **EVT-2.3** WHEN Kafka acknowledges a message THE SYSTEM SHALL mark its row as published
  (`published_at`).
- **EVT-2.4** IF publishing fails THEN THE SYSTEM SHALL leave the row unpublished and retry on the
  next poll (at-least-once delivery).
- **EVT-2.5** WHERE several instances run THE SYSTEM SHALL not publish the same row concurrently
  (`FOR UPDATE SKIP LOCKED`).
- **EVT-2.6** WHERE several instances run THE SYSTEM SHALL still publish the events of one order
  in the order they occurred: a row is picked only when no older unpublished row of the same
  order exists.

### EVT-3 Event contract

- **EVT-3.1** THE SYSTEM SHALL publish JSON messages matching `contracts/dispatch-order-event.v1.schema.json`.
- **EVT-3.2** Each message SHALL contain `eventId`, `eventType`, `eventVersion` (= 1),
  `occurredAt`, `orderId` and a `data` object with `status`, `vesselName`, `vesselImo`, `berth`,
  `fuelType`, `quantityM3` and, for cancellations, `reason`.
- **EVT-3.3** THE SYSTEM SHALL also set Kafka headers `eventType` and `eventId`.

## Non-functional

- **EVT-NF-1** Published rows older than 7 days are deleted by a scheduled cleanup.

## Out of scope

Schema Registry / Avro (possible future ADR), consumers (phase 04).
