# Phase 05 — Resilience & observability · Design

Status: approved

## Components

| Component | Where | Notes |
| --- | --- | --- |
| `KafkaErrorHandlingConfig` | tracking `config` | `DefaultErrorHandler(DeadLetterPublishingRecoverer, ExponentialBackOffWithMaxRetries(3))`; non-retryable: `DeserializationException`, `UnknownEventTypeException`, `DomainValidationException`; `ErrorHandlingDeserializer` wrapping the JSON deserializer |
| DLT topic | `KafkaTopicConfig` | `dispatch.orders.v1.DLT` |
| Circuit breaker + time limiter | tracking `adapter.out.persistence` | Resilience4j Reactor operators (`CircuitBreakerOperator`, `TimeLimiter`) around repository reads; instance `trackingMongo` in `application.yml` |
| `TrackingUnavailableException` | tracking `application` | → 503 ProblemDetail |
| Business metrics | services in both modules | `MeterRegistry` injected through a small port `OrderMetrics` so the application layer stays framework-free |
| `OutboxBacklogGauge` | dispatch `adapter.out.messaging` | Gauge = `count(*) where published_at is null`, cached 5 s |
| Structured logging | `logback-spring.xml` or Spring Boot structured logging (`logging.structured.format.console=ecs`) | Profile `docker` |
| Prometheus | `observability/prometheus.yml` | Scrapes both services every 5 s |
| Grafana provisioning | `observability/grafana/provisioning/{datasources,dashboards}/`, `observability/grafana/dashboards/fuel-dispatch.json` | |

## Resilience4j configuration (tracking-service)

```yaml
resilience4j:
  circuitbreaker:
    instances:
      trackingMongo:
        sliding-window-size: 20
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
        permitted-number-of-calls-in-half-open-state: 5
  timelimiter:
    instances:
      trackingMongo:
        timeout-duration: 2s
```

## Key decisions

| Decision | Why | ADR |
| --- | --- | --- |
| Retry in Kafka error handler, circuit breaker on HTTP reads | Different failure modes: consumer can wait, HTTP callers cannot | 0005 |
| Metrics behind an application port | Keeps Micrometer out of the application layer (hexagonal rule) | 0005 |

## Testing strategy

| Requirement IDs | Test | Type |
| --- | --- | --- |
| RES-1.x | `DeadLetterIT` — poison message and always-failing handler end in DLT with headers | IT (Kafka + Mongo) |
| RES-2.1, 2.2, 2.4 | `TrackingCircuitBreakerTest` — repository mock fails/slow; 503 after threshold; recovers | unit/slice |
| RES-2.3 | `KafkaDownIT` — stop Kafka container; POST still 201; `dispatch_outbox_pending` > 0 | IT |
| OBS-1.x | `MetricsIT` — `/actuator/prometheus` contains the named metrics | IT |
| OBS-2.x | Manual: screenshot of the dashboard in `docs/diagrams/`, linked from README | manual |
