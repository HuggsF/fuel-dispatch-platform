# Phase 06 — Delivery · Tasks

Rules: one task = one commit. Tick only when the check in `design.md` passes.

- [ ] **06.1** CI hardening: reports as artifacts, coverage gate, PR coverage comment.
  - _Req: DEL-1.1, DEL-1.3_
- [ ] **06.2** Release workflow: images to GHCR with SHA and `latest`.
  - _Req: DEL-1.2_
- [ ] **06.3** Security scan (Trivy on images or OWASP dependency-check).
  - _Req: DEL-1.4_
- [ ] **06.4** Kubernetes base for both services (Deployment, Service, ConfigMap, probes, resources).
  - _Req: DEL-2.2_
- [ ] **06.5** Kubernetes infra (dev StatefulSets) and local overlay; kind instructions in README.
  - _Req: DEL-2.1, DEL-2.4_
- [ ] **06.6** HPA + metrics-server note.
  - _Req: DEL-2.3_
- [ ] **06.7** README final pass (all sections of DEL-3.1) + badges.
  - _Req: DEL-1.5, DEL-3.1_
- [ ] **06.8** ADR 0006 and review of ADRs 0001–0005 (status Accepted).
  - _Req: DEL-3.2_
- [ ] **06.9** Verify phase: `/spec-review 06`; fresh clone → follow README → everything runs.
