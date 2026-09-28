# Phase 00 — Foundation · Requirements

Status: approved
Depends on: —

## Goal

A buildable, empty multi-module Maven project with local infrastructure, CI and a project board,
so that every later phase only adds features. Demonstrates dependency management, Docker and
agile tooling.

## Requirements

### FND-1 Build

**User story:** As a developer, I want one command that builds and tests everything, so that
quality is checked the same way locally and in CI.

- **FND-1.1** THE SYSTEM SHALL be a Maven multi-module project with a parent POM and the modules
  `dispatch-service` and `tracking-service`.
- **FND-1.2** THE SYSTEM SHALL include the Maven Wrapper so that `./mvnw verify` (and
  `mvnw.cmd verify` on Windows) works without a local Maven install.
- **FND-1.3** THE SYSTEM SHALL target Java 21 and manage all dependency versions in the parent POM.
- **FND-1.4** WHEN `./mvnw verify` runs THE SYSTEM SHALL compile, run unit tests (Surefire) and
  integration tests (Failsafe), and check formatting (Spotless).
- **FND-1.5** WHEN each service starts THE SYSTEM SHALL expose `GET /actuator/health` returning `UP`.

### FND-2 Local infrastructure

**User story:** As a developer or reviewer, I want to start all infrastructure with one command,
so that the project runs on any machine with Docker.

- **FND-2.1** WHEN `docker compose up -d` runs THE SYSTEM SHALL start PostgreSQL 16, MongoDB 7 and
  Kafka (KRaft mode, no ZooKeeper) on the ports in `tech.md`.
- **FND-2.2** THE SYSTEM SHALL define health checks for each infrastructure container.
- **FND-2.3** WHERE the `apps` profile is enabled THE SYSTEM SHALL also build and run both services.
- **FND-2.4** THE SYSTEM SHALL keep local credentials only in `docker-compose.yml` and
  `.env.example`; no secrets in source code.

### FND-3 Continuous integration

**User story:** As a reviewer, I want every push checked automatically, so that the main branch
is always green.

- **FND-3.1** WHEN code is pushed or a PR is opened THE SYSTEM SHALL run `./mvnw verify` on
  GitHub Actions with Java 21.
- **FND-3.2** THE SYSTEM SHALL show the CI status badge in the README.

### FND-4 Project management

**User story:** As the author, I want the work tracked like a Scrum team, so that the repository
shows how I plan and deliver.

- **FND-4.1** THE SYSTEM SHALL have a GitHub Projects board with columns Backlog, Sprint, In
  progress, Review, Done.
- **FND-4.2** THE SYSTEM SHALL have one milestone per phase, and one issue per task in a phase's
  `tasks.md` created before that phase starts (phases 00–02 at project setup).
- **FND-4.3** THE SYSTEM SHALL provide a PR template and a README skeleton.

## Out of scope

Any business code; cloud deployment.
