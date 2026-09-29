# ADR 0003 — Optimistic locking outside the domain

- Status: accepted
- Date: 2026-09-29
- Requirements: API-2.4, API-3.2, API-3.3

## Context

Two operators can act on the same dispatch order at the same moment, for example one approving it
while another cancels it. API-2.4 requires that one request wins and the other gets `409
CONCURRENT_MODIFICATION` instead of silently overwriting the first (a lost update).

Optimistic locking needs a version number that is compared on every update. The question is where
that version lives. The domain aggregate `DispatchOrder` is plain Java (ADR 0002) and knows nothing
about persistence; the JPA entity `DispatchOrderJpaEntity` is a separate class mapped in the
persistence adapter (API-3.2).

Every use case loads the order, applies a transition and saves it inside one transaction owned by
`OrderApplicationService` (API-3.3), and the adapter's `save` copies the aggregate into the entity
it loaded in that same transaction.

## Decision

The version is a **`@Version Long` on the JPA entity only**. The domain aggregate carries no
version, and `rehydrate` does not take one.

Because load and save happen in one transaction on the same managed entity, Hibernate issues
`UPDATE ... WHERE id = ? AND version = ?` with the version it read. If another transaction has
committed in between, no row matches, the commit fails with `OptimisticLockingFailureException`,
and `ApiExceptionHandler` maps it to `409 CONCURRENT_MODIFICATION`. A null version marks a new
entity, so Spring Data inserts it directly instead of merging.

`OptimisticLockingIT` proves it against real PostgreSQL with two interleaved transactions (the
second to commit fails, the first one's update is kept).

## Alternatives considered

| Option | Pros | Cons |
| --- | --- | --- |
| `@Version` on the entity only (chosen) | Domain stays free of persistence concerns; no mapping of versions through `rehydrate`; conflicts between concurrent requests are detected | Does not detect conflicts between separate requests (read in one, write in a later one) |
| Version in the aggregate, passed through `rehydrate` and back | Makes the version available to adapters, e.g. for an `ETag` | Leaks a persistence concept into the domain; every constructor and test carries it; no benefit until conditional requests exist |
| Pessimistic locking (`SELECT ... FOR UPDATE`) | Conflicts are serialized instead of rejected | Holds row locks for the whole use case; blocks readers of the row; worse under load |
| No locking (last write wins) | Simplest | Violates API-2.4: an approval can silently undo a cancellation |

## Consequences

- The domain and its tests are unchanged by concurrency control.
- A client that reads an order, waits, and then acts is not protected against someone else acting
  in between; the transition table still rejects impossible moves (e.g. approving a cancelled
  order), but not a stale view. Protecting that case needs `ETag` / `If-Match` on the API and the
  version in the aggregate; that is out of scope for phase 02 and would supersede this ADR.
- The guarantee depends on the use case loading and saving in one transaction. A future adapter
  that saves an aggregate loaded in another transaction would bypass the check.
