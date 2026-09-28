# Phase 01 — Dispatch domain · Design

Status: approved

## Overview

All types live in `com.fueldispatch.dispatch.domain` (plain Java 21). The aggregate root
`DispatchOrder` protects its invariants; value objects validate themselves in their constructors;
the lifecycle is encoded in `OrderStatus` so that allowed transitions live in one place.

## Components

| Type | Kind | Responsibility |
| --- | --- | --- |
| `DispatchOrder` | class (aggregate root) | State, transitions, event registration. Static factory `create(...)`, `rehydrate(...)` for persistence |
| `OrderId` | record | Wraps `UUID`; `OrderId.newId()` |
| `Vessel` | record (`name`, `imo`) | Validates non-blank name and 7-digit IMO |
| `Berth` | record (`code`) | Non-blank, trimmed, upper-case |
| `FuelType` | enum | `MGO`, `VLSFO`, `HFO` |
| `Quantity` | record (`BigDecimal cubicMetres`) | 0 < q ≤ 10,000; scale 3 |
| `DeliveryWindow` | record (`Instant start`, `Instant end`) | `end` after `start` |
| `CancellationReason` | record (`String value`) | Non-blank, ≤ 500 chars |
| `OrderStatus` | enum | `canTransitionTo(OrderStatus)`; terminal flags |
| `OrderAction` | enum | `APPROVE`, `DISPATCH`, `DELIVER`, `CANCEL` — used in error messages; `targetStatus()` gives the status each action leads to |
| `DomainEvent` | sealed interface | `eventId()`, `orderId()`, `occurredAt()` |
| `OrderCreated`, `OrderApproved`, `OrderDispatched`, `OrderDelivered`, `OrderCancelled` | records implementing `DomainEvent` | Event payloads |
| `DomainValidationException` | runtime exception | Invalid input (DOM-1.2–1.5, 2.6) |
| `InvalidOrderTransitionException` | runtime exception | Carries `currentStatus`, `action` (DOM-2.5) |

`java.time.Clock` is passed into `create` and each transition, so tests control time without
mocks (`Clock.fixed`).

### Aggregate and event details

- `DispatchOrder.create(vessel, berth, fuelType, quantity, deliveryWindow, clock)`: a missing
  order component throws `DomainValidationException`; a missing `clock` is a programming error
  (`NullPointerException`). On creation `updatedAt` equals `createdAt`.
- `domainEvents()` returns a read-only copy of the events registered and not yet pulled, so tests
  (and task 01.4's "no event on invalid transition") can inspect them without clearing;
  `pullDomainEvents()` (DOM-3.1) is the method the application layer uses.
- `DispatchOrder.rehydrate(id, vessel, berth, fuelType, quantity, deliveryWindow, status,
  createdAt, updatedAt, cancellationReason)` rebuilds a persisted order and registers no events.
  All components except `cancellationReason` are required (`DomainValidationException`), and
  `cancellationReason` must be present for `CANCELLED` and `null` otherwise, so corrupt rows are
  rejected instead of producing an order that breaks the invariants. `create` goes through the
  same checks.
- `pullDomainEvents()` returns an immutable list of the pending events in registration order and
  clears them (DOM-3.1).
- Transitions: `approve(clock)`, `dispatch(clock)`, `deliver(clock)`,
  `cancel(CancellationReason reason, clock)`. Each checks the transition table before touching any
  state, so a rejected call changes nothing (DOM-2.5). `cancel` rejects a missing reason with
  `DomainValidationException` before checking the transition; blank/too-long reasons are already
  impossible because `CancellationReason` validates itself (DOM-2.6).
  `cancellationReason()` returns `Optional<CancellationReason>`, present only once cancelled.
- `InvalidOrderTransitionException` exposes `currentStatus()` and `action()`; its message is
  `cannot <ACTION> an order in status <STATUS>`.
- Every event gets a random `eventId` (UUID) and `occurredAt` from the clock (DOM-3.2).
  `OrderCreated` carries the full order snapshot — `status`, `vessel`, `berth`, `fuelType`,
  `quantity`, `deliveryWindow` — so consumers can build their view without calling the service.
  `OrderApproved`, `OrderDispatched`, `OrderDelivered` carry the new `status`; `OrderCancelled`
  also carries the `reason`.

### Value object validation details

- `null` for any component is rejected with `DomainValidationException` (never a bare NPE), so
  adapters can map every invalid input to the same error.
- Text values are stored trimmed (`String.strip()`): vessel name, IMO, berth, cancellation reason.
  The 500-character limit of `CancellationReason` applies to the trimmed value.
- `Vessel.imo` must match `[0-9]{7}` (ASCII digits only; the IMO check digit is not validated).
- `Quantity` rejects values with more than 3 significant decimal places (e.g. `1.2345`) instead of
  rounding them — silently changing a fuel volume is not acceptable. Trailing zeros are ignored
  (`1.50000` is valid). Accepted values are normalized to scale 3, so `7.5` equals `7.500`.

## Transition table (single source of truth in `OrderStatus`)

A cell (from, action) is allowed iff `from.canTransitionTo(action.targetStatus())`; the result is
`action.targetStatus()`.

| From \ Action | APPROVE | DISPATCH | DELIVER | CANCEL |
| --- | --- | --- | --- | --- |
| CREATED | APPROVED | ✗ | ✗ | CANCELLED |
| APPROVED | ✗ | DISPATCHED | ✗ | CANCELLED |
| DISPATCHED | ✗ | ✗ | DELIVERED | ✗ |
| DELIVERED | ✗ | ✗ | ✗ | ✗ |
| CANCELLED | ✗ | ✗ | ✗ | ✗ |

## Key decisions

| Decision | Why | ADR |
| --- | --- | --- |
| Hexagonal architecture | Domain testable in isolation; adapters swappable | 0002 |
| Events registered in the aggregate, pulled by the application service | Keeps the domain free of messaging; enables outbox in phase 03 | 0002 |
| Sealed `DomainEvent` | Exhaustive `switch` in mappers; compiler catches missing cases | — |
| `Clock` injection | Deterministic tests | — |

## Testing strategy

| Requirement IDs | Test class | Type |
| --- | --- | --- |
| DOM-1.2–1.5 | `QuantityTest`, `DeliveryWindowTest`, `VesselTest`, `BerthTest` (+ `OrderIdTest`, `FuelTypeTest`) | unit |
| DOM-1.1, 1.6, 2.x, 3.x | `DispatchOrderTest` (parameterized over the transition table) | unit |
| DOM-2.6 | `CancellationReasonTest` | unit |
| DOM-4.x | `HexagonalArchitectureTest` (ArchUnit) | unit |

## Traceability

| Requirement | Section |
| --- | --- |
| DOM-1.x | Components (value objects, `create`) |
| DOM-2.x | Transition table, `OrderStatus` |
| DOM-3.x | `DomainEvent` family |
| DOM-4.x | Testing strategy (ArchUnit) |
