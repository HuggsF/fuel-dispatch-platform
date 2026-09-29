# Phase 00 — Foundation · Design

Status: approved

## Overview

Parent POM inherits from `spring-boot-starter-parent` and declares both modules. Each module is a
Spring Boot application with only Actuator (and Web / WebFlux respectively), so the build, the
health endpoint and the containers can be proven before any business code exists.

## Components

| Component | File | Notes |
| --- | --- | --- |
| Parent POM | `pom.xml` | `packaging=pom`; properties for Java 21 and library versions; `dependencyManagement` for Testcontainers BOM, springdoc, Resilience4j, ArchUnit; `pluginManagement` for Surefire, Failsafe, JaCoCo, Spotless |
| Maven Wrapper | `mvnw`, `mvnw.cmd`, `.mvn/wrapper/` | Generated with `mvn wrapper:wrapper` |
| dispatch-service | `dispatch-service/pom.xml`, `DispatchServiceApplication` | Starters: web, actuator, validation. Port 8081 |
| tracking-service | `tracking-service/pom.xml`, `TrackingServiceApplication` | Starters: webflux, actuator. Port 8082 |
| Smoke tests | `*ApplicationTest` in each module | `@SpringBootTest` + `/actuator/health` returns `UP` (FND-1.5) |
| Compose | `docker-compose.yml`, `.env.example` | Services: `postgres`, `mongo`, `kafka` (image `apache/kafka`, KRaft single node); apps under profile `apps`. Kafka UI (optional) is added in phase 03, task 03.4 |
| Dockerfiles | `*/Dockerfile` | Multi-stage: build with Maven + JDK 21, run on a JRE 21 image as non-root |
| CI | `.github/workflows/ci.yml` | `actions/setup-java` (temurin 21, maven cache) → `./mvnw -B verify` |
| PR template | `.github/pull_request_template.md` | Already created |

Note: in phase 00 the dispatch-service has no datasource yet. Do **not** add JPA/Flyway until
phase 02, so the app starts without PostgreSQL.

## Key decisions

| Decision | Why | ADR |
| --- | --- | --- |
| Maven over Gradle | Most common in enterprise Java shops; the job lists both | 0001 |
| Monorepo, multi-module | One place to review; shared build config | 0001 |
| KRaft Kafka | ZooKeeper is removed in Kafka 4.x | — |

## Testing strategy

| Requirement IDs | Test | Type |
| --- | --- | --- |
| FND-1.1–1.4 | `./mvnw verify` in CI | build |
| FND-1.5 | `DispatchServiceApplicationIT` (Testcontainers PostgreSQL since phase 02), `TrackingServiceApplicationTest` | Spring Boot test |
| FND-2.x | Manual: `docker compose up -d && docker compose ps` all healthy | manual, documented in README |

## Traceability

| Requirement | Section |
| --- | --- |
| FND-1.x | Components: parent POM, wrapper, modules, smoke tests |
| FND-2.x | Components: compose, Dockerfiles |
| FND-3.x | Components: CI |
| FND-4.x | Done manually on GitHub; README skeleton |
