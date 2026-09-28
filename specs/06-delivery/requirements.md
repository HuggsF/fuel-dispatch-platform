# Phase 06 — Delivery · Requirements

Status: approved
Depends on: phase 05

## Goal

A complete delivery pipeline and deployable manifests, plus the documentation that makes the
repository self-explanatory to a reviewer. Demonstrates CI/CD, DevOps and Kubernetes.

## Requirements

### DEL-1 Pipeline

- **DEL-1.1** WHEN a PR is opened THE SYSTEM SHALL run: format check, unit tests, integration
  tests, JaCoCo coverage gate, and publish the test and coverage reports as artifacts.
- **DEL-1.2** WHEN code is merged to `main` THE SYSTEM SHALL build both Docker images and push them
  to GitHub Container Registry tagged with the commit SHA and `latest`.
- **DEL-1.3** IF coverage on `domain` + `application` drops below 80% THEN THE SYSTEM SHALL fail the
  pipeline.
- **DEL-1.4** THE SYSTEM SHALL run a dependency vulnerability scan (e.g. OWASP dependency-check or
  Trivy on the images) and report findings.
- **DEL-1.5** THE SYSTEM SHALL show CI and coverage badges in the README.

### DEL-2 Kubernetes

- **DEL-2.1** THE SYSTEM SHALL provide manifests in `deploy/k8s/` (Kustomize base) to run both
  services plus PostgreSQL, MongoDB and Kafka on a local kind cluster.
- **DEL-2.2** Each service SHALL have a Deployment with resource requests/limits, liveness and
  readiness probes on Actuator health groups, a Service, and configuration via ConfigMap/Secret.
- **DEL-2.3** THE SYSTEM SHALL provide an HPA for each service (CPU 70%, 1–3 replicas).
- **DEL-2.4** WHEN `kubectl apply -k deploy/k8s/overlays/local` runs on kind THE SYSTEM SHALL become
  healthy, and the README SHALL document the exact commands.

### DEL-3 Documentation

- **DEL-3.1** THE README SHALL contain: one-line pitch, badges, architecture diagram, stack,
  "What this project demonstrates" (job requirement → folder), how to run, API examples, ADR links,
  test strategy, roadmap, author.
- **DEL-3.2** THE SYSTEM SHALL have ADRs 0001–0006 accepted in `docs/adr/`.

## Out of scope

Cloud deployment (phase 07), Helm charts.
