# Phase 05 — Resilience & observability · Tasks

Rules: one task = one commit. Test first. Tick only when `./mvnw verify` is green.

- [ ] **05.1** Kafka retry + DLT
  - Test: `DeadLetterIT`.
  - Code: `KafkaErrorHandlingConfig`, `ErrorHandlingDeserializer`, DLT topic.
  - _Req: RES-1.1, RES-1.2, RES-1.3_
- [ ] **05.2** Circuit breaker and timeout on tracking reads
  - Test: `TrackingCircuitBreakerTest`.
  - Code: Resilience4j dependency + config, operators in the adapter, 503 mapping.
  - _Req: RES-2.1, RES-2.2, RES-2.4_
- [ ] **05.3** Metrics port and business metrics
  - Test: unit tests with `SimpleMeterRegistry`; `MetricsIT`.
  - Code: `OrderMetrics` / `TrackingMetrics` ports + Micrometer adapters, `OutboxBacklogGauge`,
    `micrometer-registry-prometheus`.
  - _Req: OBS-1.1, OBS-1.2, OBS-1.3, RES-2.3_
- [ ] **05.4** Kafka-down scenario
  - Test: `KafkaDownIT`.
  - _Req: RES-2.3_
- [ ] **05.5** Prometheus + Grafana provisioning
  - Code: compose services, `observability/` files, dashboard JSON with the panels in OBS-2.2.
  - _Req: OBS-2.1, OBS-2.2_
- [ ] **05.6** Structured JSON logs with MDC
  - Test: log output in `docker` profile is JSON and contains `orderId`.
  - _Req: OBS-2.3_
- [ ] **05.7** ADR 0005; README section "Failure scenarios" (how to stop Mongo/Kafka and watch the dashboard).
- [ ] **05.8** Verify phase: `/spec-review 05`; dashboard screenshot committed.
