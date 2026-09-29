# Phase 02 — Dispatch API · Tasks

Rules: one task = one commit. Test first. Tick only when `./mvnw verify` is green.

- [x] **02.1** Ports and application service
  - Test: `OrderApplicationServiceTest` — create saves once; each action loads, transitions, saves;
    unknown id throws `OrderNotFoundException`.
  - Code: `port.in`, `port.out`, `OrderApplicationService`, `OrderNotFoundException`.
  - _Req: API-1.1, API-1.3, API-1.4, API-2.1, API-2.2, API-3.3_
- [x] **02.2** Flyway migration and JPA adapter
  - Test: `OrderPersistenceIT` — save + findById round-trip; paging by status, newest first.
  - Code: JPA + Flyway + PostgreSQL driver deps, `V1__create_dispatch_order.sql`, entity, mapper,
    adapter; datasource config pointing to compose / Testcontainers.
  - _Req: API-1.5, API-3.1, API-3.2_
- [x] **02.3** Optimistic locking
  - Test: `OptimisticLockingIT` — two interleaved transactions load the same order and approve
    it; the first commits, the second fails with `OptimisticLockingFailureException`.
  - Code: `@Version` on the JPA entity (the domain stays unaware of versions).
  - _Req: API-2.4_
- [x] **02.4** REST controller — create and read
  - Test: `OrderControllerTest` for POST (201, Location, 400 per field) and GET (200, 404), list.
  - Code: `OrderController`, DTOs with Bean Validation, `ApiExceptionHandler` (VALIDATION_FAILED,
    ORDER_NOT_FOUND), `ClockConfig`, `UseCaseConfig`.
  - _Req: API-1.1, API-1.2, API-1.3, API-1.4, API-1.5, API-NF-1_
- [x] **02.5** REST controller — lifecycle actions
  - Test: approve/dispatch/deliver/cancel happy paths; 409 `INVALID_TRANSITION` body; 400 on blank reason.
  - Code: action endpoints, INVALID_TRANSITION in `ApiExceptionHandler`.
  - _Req: API-2.1, API-2.2, API-2.3_
- [ ] **02.6** Global error handling
  - Test: each error code produces the ProblemDetail shape in `design.md`, including
    `CONCURRENT_MODIFICATION`, domain-rule violations and malformed JSON.
  - Code: remaining handlers in `ApiExceptionHandler`.
  - _Req: API-1.2, API-1.4, API-2.3, API-2.4_
- [ ] **02.7** OpenAPI + end-to-end IT
  - Test: `OpenApiIT`; `OrderApiIT` runs create → approve → dispatch → deliver against real Postgres.
  - Code: springdoc dependency, API metadata.
  - _Req: API-4.1_
- [ ] **02.8** Verify phase: `/spec-review 02`, coverage ≥ 80% on `application`, README "How to run"
  with `curl` examples.
  - _Req: API-NF-2_
