# Phase 00 — Foundation · Tasks

Rules: one task = one commit. Test first. Tick only when `./mvnw verify` is green.

- [x] **00.1** Parent POM, modules and Maven Wrapper
  - Test: `./mvnw -v` and `./mvnw verify` succeed on an empty build.
  - Code: `pom.xml` (parent), `dispatch-service/pom.xml`, `tracking-service/pom.xml`, wrapper files.
    Keep the existing `package-info.java` files.
  - _Req: FND-1.1, FND-1.2, FND-1.3_
- [x] **00.2** Spring Boot applications with health endpoint
  - Test: `DispatchServiceApplicationTest` and `TrackingServiceApplicationTest` call
    `/actuator/health` and assert `UP`.
  - Code: application classes, `application.yml` with ports 8081 / 8082.
  - _Req: FND-1.5_
- [ ] **00.3** Build plugins: Surefire, Failsafe, JaCoCo, Spotless
  - Test: a trivial `*IT` runs in `verify`; badly formatted code fails the build.
  - Code: `pluginManagement` in parent; plugins active in both modules.
  - _Req: FND-1.4_
- [ ] **00.4** Docker Compose infrastructure
  - Test: `docker compose up -d` → `docker compose ps` shows postgres, mongo, kafka healthy.
  - Code: `docker-compose.yml`, `.env.example`.
  - _Req: FND-2.1, FND-2.2, FND-2.4_
- [ ] **00.5** Dockerfiles and `apps` profile
  - Test: `docker compose --profile apps up -d --build`; both health endpoints return `UP`.
  - Code: multi-stage Dockerfiles, compose services under profile `apps`.
  - _Req: FND-2.3_
- [ ] **00.6** GitHub Actions CI
  - Test: push a branch; workflow green.
  - Code: `.github/workflows/ci.yml`; CI badge in README.
  - _Req: FND-3.1, FND-3.2_
- [ ] **00.7** Project board, milestones, issues (manual on GitHub)
  - Create board, milestones 00–07, one issue per task of phases 00–02.
  - Write ADR `docs/adr/0001-maven-multi-module-monorepo.md`.
  - _Req: FND-4.1, FND-4.2, FND-4.3_
- [ ] **00.8** Verify phase: `/spec-review 00` has no "missing" rows; README roadmap updated.
