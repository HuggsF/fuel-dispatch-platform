# Phase 02 — Dispatch API · Design

Status: approved

## Overview

Driving adapter (REST) → driving ports (use cases) → application services → driven port
`OrderRepository` → persistence adapter (JPA/PostgreSQL). Services are the transaction boundary.

## Components

| Component | Package | Notes |
| --- | --- | --- |
| `CreateOrderUseCase`, `ChangeOrderStatusUseCase`, `GetOrderQuery`, `ListOrdersQuery` | `application.port.in` | Interfaces + records `CreateOrderCommand`, `ChangeStatusCommand(orderId, action, reason)`, `OrderPageQuery` |
| `OrderRepository` | `application.port.out` | `save`, `findById`, `findPage(status, page, size)` |
| `OrderApplicationService` | `application.service` | Implements the ports; `@Transactional`; uses `Clock` bean |
| `OrderNotFoundException` | `application` | → 404 |
| `OrderController` | `adapter.in.web` | Endpoints below |
| `CreateOrderRequest`, `CancelOrderRequest`, `OrderResponse`, `PageResponse<T>` | `adapter.in.web.dto` | Records with Bean Validation |
| `ApiExceptionHandler` | `adapter.in.web` | `@RestControllerAdvice` → ProblemDetail with `code` |
| `DispatchOrderJpaEntity`, `SpringDataOrderRepository`, `JpaOrderRepositoryAdapter`, `OrderJpaMapper` | `adapter.out.persistence` | `@Version` column for optimistic locking |
| `ClockConfig`, `UseCaseConfig`, `OpenApiConfig` | `config` | Wiring; API metadata (title, version, description) for springdoc (API-4.1) |

### Port signatures

| Port | Method | Returns |
| --- | --- | --- |
| `CreateOrderUseCase` | `create(CreateOrderCommand)` | `DispatchOrder` |
| `ChangeOrderStatusUseCase` | `changeStatus(ChangeStatusCommand)` | `DispatchOrder` |
| `GetOrderQuery` | `get(OrderId)` | `DispatchOrder` (throws `OrderNotFoundException`) |
| `ListOrdersQuery` | `list(OrderPageQuery)` | `OrderPage` |
| `OrderRepository` | `save(DispatchOrder)` / `findById(OrderId)` / `findPage(Optional<OrderStatus>, int page, int size)` | `DispatchOrder` / `Optional<DispatchOrder>` / `OrderPage` |

- Commands carry domain value objects (`Vessel`, `Berth`, `FuelType`, `Quantity`,
  `DeliveryWindow`); the web adapter builds them from the DTO, so domain rule violations surface
  as `DomainValidationException` → 400.
- `ChangeStatusCommand(OrderId orderId, OrderAction action, CancellationReason reason)`: `reason`
  is required when `action == CANCEL` and must be null otherwise (checked in the compact
  constructor, `IllegalArgumentException`; a missing `orderId`/`action` is a `NullPointerException`).
- `OrderPageQuery(OrderStatus status /* nullable */, int page, int size)`: `page >= 0`,
  `1 <= size <= 100` (`IllegalArgumentException`); defaults (0, 20) are applied by the web adapter.
- `OrderPage(List<DispatchOrder> items, int page, int size, long totalElements)` lives in
  `application.port.out` and is also returned by `ListOrdersQuery` (no Spring Data types leak).
- The service returns the aggregate returned by `OrderRepository.save`; adapters may depend on
  `domain` (structure.md).
- `OrderApplicationService` is a plain class (no `@Service`): `UseCaseConfig` registers it as a bean
  once the persistence adapter exists, so the application context never needs a missing
  `OrderRepository`. Commands are `@Transactional`, queries `@Transactional(readOnly = true)`
  (API-3.3); `spring-tx` is added to `dispatch-service` for the annotation.

## Endpoints

| Method | Path | Body | Success | Errors |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/orders` | `CreateOrderRequest` | 201 + `Location` | 400 |
| GET | `/api/v1/orders/{id}` | — | 200 | 404 |
| GET | `/api/v1/orders?status=&page=&size=` | — | 200 `PageResponse` | 400 |
| POST | `/api/v1/orders/{id}/approve` | — | 200 | 404, 409 |
| POST | `/api/v1/orders/{id}/dispatch` | — | 200 | 404, 409 |
| POST | `/api/v1/orders/{id}/deliver` | — | 200 | 404, 409 |
| POST | `/api/v1/orders/{id}/cancel` | `{ "reason": "..." }` | 200 | 400, 404, 409 |

`CreateOrderRequest`: `vesselName`, `vesselImo`, `berth`, `fuelType`, `quantityM3`,
`windowStart`, `windowEnd`.

## Web details

- `CreateOrderRequest` repeats the domain rules as Bean Validation so API-1.2 can list every
  invalid field at once: `vesselName` not blank, ≤ 120; `vesselImo` `[0-9]{7}`; `berth` not
  blank, ≤ 20; `fuelType` a string matching `MGO|VLSFO|HFO`; `quantityM3` > 0, ≤ 10000, at most
  3 decimals; `windowStart`, `windowEnd` required, end after start (reported on `windowEnd`).
  The lengths also match the column sizes. `DomainValidationException` stays as a safety net
  (400 without a field list, task 02.6).
- `OrderResponse`: `id`, `vesselName`, `vesselImo`, `berth`, `fuelType`, `quantityM3`,
  `windowStart`, `windowEnd`, `status`, `cancellationReason` (null unless cancelled),
  `createdAt`, `updatedAt`. `PageResponse<T>`: `items`, `page`, `size`, `totalElements`,
  `totalPages`.
- Mapping lives in the DTOs (`CreateOrderRequest.toCommand()`, `OrderResponse.from(order)`); the
  controller only maps and delegates (API-NF-1). `Location` is `/api/v1/orders/{id}`.
- List parameters: `status` an `OrderStatus` name, `page` ≥ 0 (default 0), `size` 1–100
  (default 20); out-of-range values → 400 (method validation).
- Validation errors → 400, `code: VALIDATION_FAILED`, `errors: [{ "field", "message" }]`,
  sorted by field.
- `ApiExceptionHandler` grows with the tasks: 02.4 `VALIDATION_FAILED` and `ORDER_NOT_FOUND`;
  02.5 `INVALID_TRANSITION`; 02.6 `CONCURRENT_MODIFICATION`, `DomainValidationException`,
  malformed JSON, plus the test of every code's shape.
- `ClockConfig` (`Clock.systemUTC()`) and `UseCaseConfig` (registers `OrderApplicationService`)
  are created in 02.4, the first task that needs the use cases as beans.
- Error codes (every error body has `code`):

  | Situation | Status | `code` | Extra properties |
  | --- | --- | --- | --- |
  | Bean Validation on body, path or query | 400 | `VALIDATION_FAILED` | `errors` |
  | Wrong JSON type for a known field (e.g. `"quantityM3": "abc"`), bad UUID in the path, unknown `status` | 400 | `VALIDATION_FAILED` | `errors` (that one field, "has an invalid value") |
  | `DomainValidationException` (safety net; the DTOs normally catch it first) | 400 | `VALIDATION_FAILED` | none (domain message in `detail`) |
  | Unparseable or missing body | 400 | `MALFORMED_REQUEST` | — |
  | `OrderNotFoundException` | 404 | `ORDER_NOT_FOUND` | — |
  | `InvalidOrderTransitionException` | 409 | `INVALID_TRANSITION` | `currentStatus`, `action` |
  | `OptimisticLockingFailureException` | 409 | `CONCURRENT_MODIFICATION` | — ("reload the order and retry") |
  | Other Spring MVC errors (unknown route, 405, 415, 406) | as Spring decides | the `HttpStatus` name, e.g. `METHOD_NOT_ALLOWED` | — |
  | Anything else | 500 | `INTERNAL_ERROR` | — (generic `detail`, exception logged, never exposed) |

- `ApiExceptionHandlerTest` (`@WebMvcTest`, use cases mocked) checks the full shape of each
  row: `type`, `title`, `status`, `detail`, `code`, `application/problem+json`.

Error body example:

```json
{
  "type": "about:blank",
  "title": "Invalid order transition",
  "status": 409,
  "detail": "Cannot DISPATCH an order in status CREATED",
  "code": "INVALID_TRANSITION",
  "currentStatus": "CREATED",
  "action": "DISPATCH"
}
```

## Schema — `V1__create_dispatch_order.sql`

| Column | Type | Notes |
| --- | --- | --- |
| `id` | `uuid` PK | from domain |
| `vessel_name` | `varchar(120)` not null | |
| `vessel_imo` | `char(7)` not null | |
| `berth` | `varchar(20)` not null | |
| `fuel_type` | `varchar(10)` not null | |
| `quantity_m3` | `numeric(10,3)` not null | check > 0 |
| `window_start`, `window_end` | `timestamptz` not null | check end > start |
| `status` | `varchar(20)` not null | index |
| `cancellation_reason` | `varchar(500)` null | |
| `created_at`, `updated_at` | `timestamptz` not null | index on `created_at desc` |
| `version` | `bigint` not null | optimistic locking |

## Persistence details

- Dependencies: `spring-boot-starter-data-jpa`, `flyway-core` + `flyway-database-postgresql`,
  `postgresql` (runtime); tests: `spring-boot-testcontainers`, Testcontainers `postgresql`.
- `application.yml`: datasource `jdbc:postgresql://localhost:5432/dispatch` (compose defaults),
  overridable through `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`; `ddl-auto: validate`;
  `open-in-view: false`. Compose `apps` profile sets the URL to host `postgres` and waits for
  its healthcheck.
- Tests share `TestcontainersConfiguration` (`@ServiceConnection` `postgres:16.15-alpine`, the
  compose image). `OrderPersistenceIT` is a `@DataJpaTest` importing the adapter and mapper.
  Because the application context now needs a database, the phase 00 smoke test becomes
  `DispatchServiceApplicationIT` using the same configuration.
- `findPage` orders by `created_at desc, id desc` (id breaks ties so pages are stable);
  the status filter is a derived query `findByStatus(status, Pageable)`.
- `save` loads the row by id and copies the aggregate into it (insert when absent), so an
  update touches the entity already managed in the use case's transaction.
- `version` is a `@Version Long` on the entity only (null → new row, so Spring Data persists
  instead of merging). Because each use case loads and saves the same managed entity in one
  transaction (API-3.3), Hibernate's `UPDATE ... WHERE version = ?` detects a concurrent commit
  and the transaction fails with `OptimisticLockingFailureException` (API-2.4). The domain
  aggregate carries no version; conflicts between separate requests (read, then write later)
  would need ETag/If-Match and are out of scope.

## Key decisions

| Decision | Why | ADR |
| --- | --- | --- |
| Separate JPA entity from domain | Domain stays framework-free; persistence can change | 0002 |
| Action endpoints (`/approve`) instead of `PATCH status` | Explicit intent, maps 1:1 to use cases | — |
| ProblemDetail + `code` | Standard (RFC 9457) and machine-readable | — |

## Testing strategy

| Requirement IDs | Test | Type |
| --- | --- | --- |
| API-1.x, 2.x (logic) | `OrderApplicationServiceTest` (Mockito for `OrderRepository`) | unit |
| API-1.x, 2.x, NF-1 (HTTP) | `OrderControllerTest` (`@WebMvcTest`, use cases mocked) | slice |
| API-3.x | `OrderPersistenceIT` (Testcontainers PostgreSQL, Flyway) | IT |
| API-2.4 | `OptimisticLockingIT` | IT |
| End to end | `OrderApiIT` (`@SpringBootTest` + Testcontainers + `RestClient`) | IT |
| API-4.1 | `OpenApiIT` — `/v3/api-docs` returns 200 | IT |
