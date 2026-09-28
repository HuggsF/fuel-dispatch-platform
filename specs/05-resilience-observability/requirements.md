# Phase 05 — Resilience & observability · Requirements

Status: approved
Depends on: phase 04

## Goal

Make both services survive failures of their dependencies and make their behaviour visible.
Demonstrates resilience techniques for distributed systems and monitoring with Prometheus/Grafana.

## Requirements

### RES-1 Consumer failures

- **RES-1.1** IF processing an event fails with a transient error THEN THE SYSTEM SHALL retry with
  exponential backoff (1 s, 2 s, 4 s; max 3 retries).
- **RES-1.2** IF all retries fail, or the message cannot be deserialized, THEN THE SYSTEM SHALL
  send it to `dispatch.orders.v1.DLT` with the original headers plus the exception class and message,
  and continue with the next message.
- **RES-1.3** IF the error is non-retryable (invalid payload, unknown event type) THEN THE SYSTEM
  SHALL send it to the DLT without retrying.

### RES-2 Protecting dependencies

- **RES-2.1** WHILE MongoDB is failing THE SYSTEM SHALL open a circuit breaker around tracking
  reads after 50% failures in a 20-call window, and answer `503` with code `TRACKING_UNAVAILABLE`
  instead of waiting.
- **RES-2.2** THE SYSTEM SHALL apply a 2-second timeout to tracking reads.
- **RES-2.3** WHILE Kafka is down THE SYSTEM SHALL keep dispatch-service accepting commands (outbox,
  EVT-1.3) and report the relay backlog as a metric.
- **RES-2.4** WHEN MongoDB recovers THE SYSTEM SHALL close the circuit (half-open with 5 trial calls).

### OBS-1 Metrics

- **OBS-1.1** THE SYSTEM SHALL expose Prometheus metrics at `/actuator/prometheus` on both services.
- **OBS-1.2** THE SYSTEM SHALL publish business metrics: `dispatch_orders_created_total`,
  `dispatch_orders_transitions_total{action}`, `dispatch_outbox_pending` (gauge),
  `tracking_events_applied_total{result}`, `tracking_sse_subscribers` (gauge).
- **OBS-1.3** THE SYSTEM SHALL expose Resilience4j and Kafka consumer-lag metrics.

### OBS-2 Dashboards and logs

- **OBS-2.1** WHEN `docker compose up` runs THE SYSTEM SHALL start Prometheus and Grafana with the
  datasource and a "Fuel Dispatch" dashboard provisioned from `observability/`.
- **OBS-2.2** The dashboard SHALL show request rate, error rate and p95 latency per endpoint,
  outbox backlog, consumer lag, DLT count and circuit breaker state.
- **OBS-2.3** THE SYSTEM SHALL log in JSON in the `docker` profile, including `orderId` and
  `eventId` in the MDC when available.

## Out of scope

Distributed tracing (optional stretch: Micrometer Tracing + OpenTelemetry), alerting rules.
