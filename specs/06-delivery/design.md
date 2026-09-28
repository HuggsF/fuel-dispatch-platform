# Phase 06 — Delivery · Design

Status: approved

## Pipeline — `.github/workflows/`

| Workflow | Trigger | Jobs |
| --- | --- | --- |
| `ci.yml` | push, pull_request | `build`: setup-java 21 (Temurin, Maven cache) → `./mvnw -B verify` → upload Surefire/Failsafe/JaCoCo reports → coverage summary comment on PR |
| `release.yml` | push to `main` | `images`: matrix over both services → `docker/build-push-action` to `ghcr.io/<user>/<service>:<sha>` and `:latest`; Trivy scan of each image (fail on CRITICAL) |

Testcontainers runs natively on GitHub-hosted Ubuntu runners (Docker available).
The job description cites Jenkins/GitLab CI: an equivalent `.gitlab-ci.yml` may be added as a
stretch to show portability (same stages: verify → build image → scan).

## Kubernetes — `deploy/k8s/`

```
deploy/k8s/
├── base/
│   ├── kustomization.yaml
│   ├── dispatch-service/  (deployment, service, configmap, hpa)
│   ├── tracking-service/  (deployment, service, configmap, hpa)
│   └── infra/             (postgres, mongo, kafka — single-replica StatefulSets, dev only)
└── overlays/local/        (kustomization with image tags, secret generator, NodePort/ingress)
```

Probes: `management.endpoint.health.probes.enabled=true` → `/actuator/health/liveness` and
`/actuator/health/readiness`. Readiness of dispatch includes the DB; tracking includes Mongo.
Resources (starting point): requests 250m CPU / 384Mi, limits 1 CPU / 768Mi. JVM flag
`-XX:MaxRAMPercentage=75`. HPA requires metrics-server on kind (documented).

## Documentation

ADRs expected by the end of this phase:

| ADR | Title |
| --- | --- |
| 0001 | Maven multi-module monorepo |
| 0002 | Hexagonal architecture and domain events |
| 0003 | Transactional outbox and at-least-once delivery |
| 0004 | Reactive read side with MongoDB and SSE |
| 0005 | Resilience strategy and metrics port |
| 0006 | CI/CD and Kubernetes deployment |

## Testing strategy

| Requirement IDs | Check |
| --- | --- |
| DEL-1.x | Open a PR with a failing test and one with low coverage; both must fail |
| DEL-2.x | `kind create cluster` → `kubectl apply -k` → all pods Ready; `curl` via port-forward |
| DEL-3.x | `/spec-review 06` + a fresh-clone run following only the README |
