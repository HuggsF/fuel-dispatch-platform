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
| `ClockConfig`, `UseCaseConfig` | `config` | Wiring |

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
