# Product steering — fuel-dispatch-platform

## Problem

At a port fuel terminal, operators schedule the delivery of marine fuel (bunkering) from terminal
tanks to vessels at a berth. Orders go through approval and dispatch, and several teams need to
see each order's status in real time. The platform replaces spreadsheets and radio calls with an
auditable workflow and a live status feed.

Inspired by a real port fuel-logistics system the author built in production (C#/.NET, 2020–2022),
rebuilt here with a Java/Spring event-driven stack.

## Actors

| Actor | Needs |
| --- | --- |
| Terminal operator | Create, approve, dispatch, deliver and cancel orders |
| Control room / monitoring | See the current status and history of any order, live |
| Integrations (future) | Consume order events from Kafka |

## Glossary

| Term | Meaning |
| --- | --- |
| Dispatch order | A request to deliver a quantity of one fuel type to a vessel at a berth within a time window |
| Vessel | Ship receiving fuel. Identified by name and IMO number (7 digits) |
| Berth | Mooring position in the terminal (e.g. `B-03`) |
| Fuel type | `MGO` (marine gas oil), `VLSFO` (very low sulphur fuel oil), `HFO` (heavy fuel oil) |
| Quantity | Volume in cubic metres (m³) |
| Delivery window | Planned start and end instants for the delivery |

## Order lifecycle

```
CREATED ──approve──▶ APPROVED ──dispatch──▶ DISPATCHED ──deliver──▶ DELIVERED
   │                    │
   └──────cancel────────┴──▶ CANCELLED
```

- `DELIVERED` and `CANCELLED` are terminal states.
- Cancelling requires a non-blank reason.
- Every state change produces a domain event that is published to Kafka.

## Business rules

| ID | Rule |
| --- | --- |
| BR-1 | Quantity must be greater than 0 and at most 10,000 m³ |
| BR-2 | Delivery window end must be after its start |
| BR-3 | IMO number must be exactly 7 digits |
| BR-4 | Only the transitions shown in the lifecycle are allowed; anything else is rejected |
| BR-5 | Cancellation reason is mandatory, max 500 characters |
| BR-6 | Every state change is recorded with its timestamp and is never lost, even if Kafka is down |

## Services

| Service | Responsibility | Owns |
| --- | --- | --- |
| `dispatch-service` | Command side: order lifecycle and business rules | PostgreSQL |
| `tracking-service` | Read side: status + history, live stream | MongoDB |

The services never call each other. The only integration is the Kafka topic `dispatch.orders.v1`.

## Out of scope

Authentication/authorization, UI, pricing and invoicing, tank inventory, multi-tenancy.
