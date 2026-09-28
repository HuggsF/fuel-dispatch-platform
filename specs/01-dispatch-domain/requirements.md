# Phase 01 — Dispatch domain · Requirements

Status: approved
Depends on: phase 00

## Goal

The heart of `dispatch-service`: the `DispatchOrder` aggregate with its rules and lifecycle, in
plain Java, fully unit-tested. Demonstrates solid OOP, DDD building blocks and the core of
hexagonal architecture.

## Requirements

### DOM-1 Create an order

**User story:** As a terminal operator, I want to register a dispatch order, so that the delivery
can be planned and approved.

- **DOM-1.1** WHEN an order is created with valid data THE SYSTEM SHALL assign a new UUID, set
  status `CREATED`, and record `createdAt` from the injected clock.
- **DOM-1.2** IF quantity ≤ 0 m³ or > 10,000 m³ THEN THE SYSTEM SHALL reject creation. (BR-1)
- **DOM-1.3** IF the delivery window end is not after its start THEN THE SYSTEM SHALL reject
  creation. (BR-2)
- **DOM-1.4** IF the IMO number is not exactly 7 digits THEN THE SYSTEM SHALL reject creation. (BR-3)
- **DOM-1.5** IF vessel name or berth is blank THEN THE SYSTEM SHALL reject creation.
- **DOM-1.6** WHEN an order is created THE SYSTEM SHALL register an `OrderCreated` domain event.

### DOM-2 Lifecycle transitions

**User story:** As a terminal operator, I want to move an order through approval, dispatch and
delivery, so that everyone knows where it stands.

- **DOM-2.1** WHEN `approve` is called on a `CREATED` order THE SYSTEM SHALL change status to
  `APPROVED` and register `OrderApproved`.
- **DOM-2.2** WHEN `dispatch` is called on an `APPROVED` order THE SYSTEM SHALL change status to
  `DISPATCHED` and register `OrderDispatched`.
- **DOM-2.3** WHEN `deliver` is called on a `DISPATCHED` order THE SYSTEM SHALL change status to
  `DELIVERED` and register `OrderDelivered`.
- **DOM-2.4** WHEN `cancel(reason)` is called on a `CREATED` or `APPROVED` order THE SYSTEM SHALL
  change status to `CANCELLED`, store the reason and register `OrderCancelled`.
- **DOM-2.5** IF a transition is not allowed from the current status THEN THE SYSTEM SHALL throw
  `InvalidOrderTransitionException` naming the current status and the attempted action, and SHALL
  NOT change state or register events. (BR-4)
- **DOM-2.6** IF the cancellation reason is blank or longer than 500 characters THEN THE SYSTEM
  SHALL reject the cancellation. (BR-5)
- **DOM-2.7** WHEN any transition succeeds THE SYSTEM SHALL update `updatedAt` from the clock.

### DOM-3 Domain events

- **DOM-3.1** THE SYSTEM SHALL expose registered events through `pullDomainEvents()`, which returns
  them in order and clears the internal list.
- **DOM-3.2** Each event SHALL carry: `eventId` (UUID), `orderId`, `occurredAt`, and the order data
  needed by consumers (status, and reason for cancellations).

### DOM-4 Architecture guard

- **DOM-4.1** THE SYSTEM SHALL enforce with ArchUnit that `..domain..` depends only on the JDK.
- **DOM-4.2** THE SYSTEM SHALL enforce with ArchUnit the layer rules in `structure.md`.

## Non-functional

- **DOM-NF-1** 100% of domain branches covered by unit tests (JaCoCo report).
- **DOM-NF-2** Domain objects are immutable where possible (value objects as records).

## Out of scope

Persistence, REST, Kafka — the domain knows none of them.
