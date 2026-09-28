# Phase 02 — Dispatch API · Requirements

Status: approved
Depends on: phase 01

## Goal

Expose the domain through use cases, a REST API and PostgreSQL persistence, completing the
hexagon of `dispatch-service`. Demonstrates Spring Boot, REST, JPA, Flyway, Mockito and
Testcontainers.

## Requirements

### API-1 Create and read orders

**User story:** As a terminal operator, I want to create and look up orders over HTTP, so that any
client can use the platform.

- **API-1.1** WHEN `POST /api/v1/orders` receives a valid body THE SYSTEM SHALL persist the order
  and return `201 Created` with a `Location` header and the order representation.
- **API-1.2** IF the body fails validation (missing fields, wrong types, domain rule violated)
  THEN THE SYSTEM SHALL return `400` with a ProblemDetail listing every invalid field.
- **API-1.3** WHEN `GET /api/v1/orders/{id}` is called for an existing order THE SYSTEM SHALL
  return `200` with the order.
- **API-1.4** IF the order does not exist THEN THE SYSTEM SHALL return `404` with code
  `ORDER_NOT_FOUND`.
- **API-1.5** WHEN `GET /api/v1/orders` is called THE SYSTEM SHALL return a page of orders, newest
  first, filterable by `status`, with `page` (default 0) and `size` (default 20, max 100).

### API-2 Lifecycle over HTTP

- **API-2.1** WHEN `POST /api/v1/orders/{id}/approve`, `/dispatch` or `/deliver` is called and the
  transition is allowed THE SYSTEM SHALL apply it and return `200` with the updated order.
- **API-2.2** WHEN `POST /api/v1/orders/{id}/cancel` is called with a valid `reason` THE SYSTEM
  SHALL cancel the order and return `200`.
- **API-2.3** IF the transition is not allowed THEN THE SYSTEM SHALL return `409` with code
  `INVALID_TRANSITION`, the current status and the attempted action.
- **API-2.4** IF two requests modify the same order concurrently THEN THE SYSTEM SHALL let one
  succeed and answer the other with `409` code `CONCURRENT_MODIFICATION` (optimistic locking).

### API-3 Persistence

- **API-3.1** THE SYSTEM SHALL store orders in PostgreSQL with the schema created only by Flyway
  migrations (`ddl-auto=validate`).
- **API-3.2** THE SYSTEM SHALL keep the JPA entity separate from the domain aggregate, mapped in the
  persistence adapter.
- **API-3.3** THE SYSTEM SHALL run each use case in a single transaction.

### API-4 Documentation

- **API-4.1** THE SYSTEM SHALL publish an OpenAPI description and Swagger UI at `/swagger-ui.html`.

## Non-functional

- **API-NF-1** Controllers contain no business logic; they map DTO ↔ command and call a use case.
- **API-NF-2** 80% line coverage on `application`; every endpoint and error code has a test.

## Out of scope

Kafka publishing (phase 03), authentication.
