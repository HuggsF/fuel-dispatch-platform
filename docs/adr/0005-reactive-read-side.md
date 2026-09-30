# ADR 0005 — Reactive read side: Spring Kafka in, WebFlux and MongoDB out, in-memory SSE fan-out

- Status: accepted
- Date: 2026-09-30
- Requirements: TRK-1.1–TRK-1.4, TRK-2.1–TRK-2.3, TRK-3.1–TRK-3.4, TRK-4.1, TRK-4.2, TRK-NF-1

## Context

`tracking-service` is the read side. The control room needs each order's current status and
history, a list by status, and a live feed of changes (TRK-2, TRK-3). The service's only input is
Kafka topic `dispatch.orders.v1` ([ADR 0004](0004-transactional-outbox-and-at-least-once-delivery.md)),
which delivers **at least once** and in order per order. So the consumer must deduplicate by
`eventId` (TRK-1.2), tolerate an old event arriving late (TRK-1.3), and commit offsets only after
the data is saved (TRK-1.4).

The live feed holds many long-lived HTTP connections that are idle most of the time. That is where
a non-blocking server pays off. Consuming Kafka is a different workload: one partition is processed
in sequence, and the offset must follow the save.

## Decision

**Reactive where it pays off (HTTP and MongoDB), a blocking Spring Kafka listener where ordering
and offsets matter, and an in-memory Reactor sink for the live feed.**

1. **WebFlux + reactive MongoDB for the API.** Ports return `Mono` / `Flux`. `GET
   /tracking?status=` streams NDJSON as documents are read. `GET /tracking/stream` is Server-Sent
   Events: `event: status-changed`, `id: <eventId>`, plus a `:heartbeat` comment every 15 s.
2. **Spring Kafka `@KafkaListener` that blocks on the use case.** It runs on the listener thread
   (`block(10 s)`, never on Netty) and acknowledges manually (`manual_immediate`) only after the
   document is saved. On a failure it rethrows without acking. A `DefaultErrorHandler` then
   retries transient failures with exponential back-off (1 s doubling, capped at 30 s) and **no
   attempt limit**, so the offset never moves past an unsaved event. Invalid payloads are not
   retryable and are logged and skipped. Group `tracking-service`, `auto-offset-reset: earliest`.
3. **One MongoDB document per order.** `order_tracking` holds the current status, the order
   summary, the history embedded and sorted by `occurredAt`, and the last 50 processed event ids.
   The aggregate `OrderTracking` returns `APPLIED`, `DUPLICATE` or `OUT_OF_ORDER_RECORDED`.
   A late event goes into the history but does not change the status.
4. **Optimistic locking beside the aggregate.** `@Version` lives on the document only (as in
   [ADR 0003](0003-optimistic-locking-outside-the-domain.md)). Reactive MongoDB has no managed
   entity, so the version read travels next to the aggregate (`VersionedTracking`) to `save`. A
   conflict becomes `ConcurrentTrackingUpdateException`, and the service retries once from a
   fresh read.
5. **In-memory fan-out with a per-client backpressure strategy.**
   `Sinks.many().multicast().directBestEffort()` replays nothing to late subscribers, and one slow
   client never holds back the others. Each subscriber gets `onBackpressureLatest()`, and the
   controller merges changes with heartbeats at prefetch 1. A client that stops reading therefore
   gets the change already in flight and then the newest one, and the ones in between are dropped
   (TRK-3.4). This bound excludes what Reactor Netty and the socket buffer had already accepted.
   Heartbeats drop instead of overflowing, because `Flux.interval` would otherwise end the stream
   of a client that stopped reading for ~8 minutes. `publish` is `synchronized` because
   the sink rejects concurrent emissions. Subscribers hear only of `APPLIED` changes, and only
   after the save.
6. **Guards.** ArchUnit applies dispatch-service's hexagonal rules. The one addition is that the
   application layer may use Reactor core types; Reactor Netty is forbidden, and so is any
   dependency on dispatch-service code (TRK-4.2). BlockHound runs in every test JVM of the module,
   including ITs with real Netty event loops (TRK-NF-1).

## Alternatives considered

| Option | Pros | Cons |
| --- | --- | --- |
| Spring Kafka listener + `block()` (chosen) | Offsets committed after the save with plain, well-known Spring Kafka; sequential per partition keeps per-order order; error handling and the phase 05 DLT come built in | A listener thread waits on I/O; throughput per partition bounded by MongoDB latency (ms, fine for a status feed) |
| reactor-kafka | End-to-end reactive pipeline | No longer actively developed; manual offset and ordering management; more code to get the same guarantees |
| MongoDB change streams for the live feed | Every instance sees every change; survives restarts | Needs a replica set, even locally; more moving parts than one instance needs |
| Redis pub/sub or a per-instance Kafka group for fan-out | Multi-instance live feed | Extra infrastructure or topic traffic before it is needed |
| `multicast().onBackpressureBuffer()` sink | Simple buffering | Buffers while nobody listens and replays to the first subscriber (breaks TRK-3.1); one slow subscriber can fill the shared buffer |
| PostgreSQL for the read model | One database technology | Status + nested history is one document read; a second relational schema adds joins and migrations for no gain; the role asks for NoSQL experience |
| Spring MVC + blocking MongoDB | Simpler threading | One thread per open SSE connection; the live feed is exactly where non-blocking pays off |

## Consequences

- The read view is **eventually consistent**: an order shows up in tracking about a second after the
  command (outbox relay + consumer). Clients must not expect read-your-writes across services.
- **Live feed and multiple instances.** Several `tracking-service` instances share the consumer
  group, so each one applies only its partitions' events. An SSE client connected to instance A
  misses changes applied by instance B. Running more than one instance needs a cross-instance
  fan-out first (see the alternatives table).
- **Instant precision.** MongoDB stores instants in milliseconds, while the contract carries
  microseconds. The SSE event shows the original `occurredAt` and the stored history the truncated
  one. Two events of the same order within one millisecond count as simultaneous, and the later
  one wins.
- **MongoDB outages stall, they do not lose.** Spring Kafka's default handler (10 attempts
  without pause, then skip) lost events after a few seconds of outage; `ConsumerFailureIT` showed
  it and now guards against it. With unbounded retries a partition waits for MongoDB, consumer lag
  grows, and processing resumes in order. Phase 05 should alert on that lag.
- **A save slower than the listener timeout is not pushed live.** The listener waits at most
  10 s for the use case. If MongoDB completes the save after that, the event is stored but never
  notified. The redelivered event is a `DUPLICATE` and is not notified either. SSE clients miss
  that one change, while `GET /tracking/{orderId}` stays correct. Accepted for now; closing the
  gap would mean remembering per event whether it was notified.
- **Poison messages.** An invalid payload (not JSON, a missing required field) is logged and
  skipped at once, so it cannot block its partition. Phase 05 sends it to a dead-letter topic
  instead. A payload that parses but always fails for another reason would be retried forever;
  the phase 05 DLT policy must decide when to give up on those.
- **BlockHound allowances.** The only allowed blocking call is framework code (Actuator's lazy
  `SingletonSupplier` lock), listed with its reason in `TrackingBlockHoundIntegration`. Any new
  entry needs the same justification. `synchronized` in `publish` is outside BlockHound's view.
  It stays safe only because the lock covers a hand-off, never I/O.
- **Deduplication is bounded.** An order keeps its last 50 event ids, while a full lifecycle has at
  most 4. Raise the bound if events per order ever grow.
