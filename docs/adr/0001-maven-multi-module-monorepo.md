# ADR 0001 — Maven multi-module monorepo

- Status: accepted
- Date: 2026-09-28
- Requirements: FND-1.1, FND-1.2, FND-1.3

## Context

The platform has two services, `dispatch-service` and `tracking-service`, that share a Java
version, a Spring Boot line, test tooling (JUnit 5, Testcontainers, ArchUnit) and quality gates
(Surefire, Failsafe, JaCoCo, Spotless). They communicate only through Kafka and have no compile-time
dependency on each other.

The repository is a portfolio piece: a reviewer should be able to clone one repository, run one
command and see the whole system build, test and start. It is maintained by one person, so the
build must not drift between services.

## Decision

Use a single Git repository with a Maven multi-module build:

- A parent `pom.xml` (`packaging=pom`) that inherits from `spring-boot-starter-parent`, lists both
  services as modules, sets Java 21, and centralizes every dependency and plugin version in
  `dependencyManagement` / `pluginManagement`.
- One module per service. Modules declare dependencies and activate plugins, but never versions.
- The Maven Wrapper (`mvnw`, `mvnw.cmd`) pins the Maven version, so `./mvnw verify` works the same
  on a developer machine, in Docker builds and in CI.
- Services stay independently deployable: each has its own Dockerfile and image, and they share
  no code module. Shared contracts are JSON Schemas in `contracts/` (phase 03), not a shared jar.

## Alternatives considered

| Option | Pros | Cons |
| --- | --- | --- |
| Maven multi-module monorepo (chosen) | One clone, one command; versions and quality gates defined once; the most common setup in enterprise Java shops | One build for both services; a broken module blocks the other in CI |
| Gradle multi-project monorepo | Faster incremental builds; flexible Kotlin DSL | Less common in the target enterprise context; build logic is code and harder to review at a glance |
| One repository per service | Fully independent pipelines and release cadence | Duplicated build config that drifts; a reviewer must clone and wire several repos; no single `verify` |
| Maven, no parent (standalone POMs in one repo) | Modules fully decoupled | Versions and plugin config duplicated in every POM |

## Consequences

- Positive: `./mvnw verify` builds and tests everything with one set of versions; upgrading Spring
  Boot or a library is a one-line change in the parent POM; CI is a single job.
- Negative: CI rebuilds both services on every push, even when only one changed. Acceptable at this
  size; revisit with path filters or `-pl` if build time grows.
- Watch: modules must not depend on each other. If shared code appears, prefer duplication or a
  contract (JSON Schema) over a shared library, to keep the services independently deployable.
- Docker builds use the repository root as context, because the reactor needs the parent POM.
