# Phase 04 — Tracking reactive · Requirements

Status: approved
Depends on: phase 03

## Goal

Build `tracking-service`: consume order events, keep each order's status and history in MongoDB,
and serve them through a reactive API with a live stream. Demonstrates reactive programming,
NoSQL modelling, idempotent consumers and microservice decoupling.

## Requirements

### TRK-1 Consume events

**User story:** As the control room, I want the tracking view updated from the event stream, so that
it never depends on the dispatch service being up.

- **TRK-1.1** WHEN an event arrives on `dispatch.orders.v1` THE SYSTEM SHALL create or update the
  tracking document of that order with the new status and append a history entry.
- **TRK-1.2** IF an event with an already processed `eventId` arrives THEN THE SYSTEM SHALL ignore
  it without error (idempotency).
- **TRK-1.3** IF an event is older than the document's latest applied event THEN THE SYSTEM SHALL
  append it to history but SHALL NOT overwrite the current status.
- **TRK-1.4** THE SYSTEM SHALL use consumer group `tracking-service` and commit offsets only after
  the document is saved.

### TRK-2 Query tracking

- **TRK-2.1** WHEN `GET /api/v1/tracking/{orderId}` is called THE SYSTEM SHALL return the current
  status, order summary and history sorted by `occurredAt`.
- **TRK-2.2** IF no tracking exists THEN THE SYSTEM SHALL return `404` with code `TRACKING_NOT_FOUND`.
- **TRK-2.3** WHEN `GET /api/v1/tracking?status=` is called THE SYSTEM SHALL stream (`Flux`) the
  matching tracking summaries.

### TRK-3 Live stream

- **TRK-3.1** WHEN a client opens `GET /api/v1/tracking/stream` (`text/event-stream`) THE SYSTEM
  SHALL push every status change applied after the connection, as Server-Sent Events.
- **TRK-3.2** WHERE `orderId` is given as a query parameter THE SYSTEM SHALL push only that
  order's changes.
- **TRK-3.3** WHILE a stream is idle THE SYSTEM SHALL send a heartbeat comment every 15 seconds.
- **TRK-3.4** IF a client is slow THEN THE SYSTEM SHALL drop the oldest buffered updates for that
  client instead of blocking others (backpressure strategy).

### TRK-4 Architecture

- **TRK-4.1** THE SYSTEM SHALL follow the same hexagonal rules (ArchUnit) as dispatch-service.
- **TRK-4.2** THE SYSTEM SHALL have no compile-time dependency on dispatch-service code; it reads
  only the JSON contract.

## Non-functional

- **TRK-NF-1** No blocking calls on Netty event-loop threads (verified with BlockHound in tests).

## Out of scope

Resilience policies and DLT (phase 05).
