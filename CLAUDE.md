# CLAUDE.md — fuel-dispatch-platform

Portfolio project: a fuel dispatch platform for port terminals, built as two Java microservices
that talk only through Kafka. Its purpose is to demonstrate, with production-grade practices,
every technical requirement of a Senior Backend role (Java/Spring, hexagonal architecture,
reactive programming, Kafka, SQL + NoSQL, tests, Docker/Kubernetes, CI/CD, observability).

## How we work: Spec-Driven Development

Code is written **only** from an approved spec. Never implement anything that is not traced to a
requirement ID.

1. Read the steering files first, every session:
   - `specs/steering/product.md` — domain, glossary, business rules
   - `specs/steering/tech.md` — stack, versions, libraries, testing rules
   - `specs/steering/structure.md` — folders, packages, hexagonal dependency rules
2. Each phase lives in `specs/NN-name/` with three files:
   - `requirements.md` — user stories + acceptance criteria in EARS format, with IDs (e.g. `DOM-3.2`)
   - `design.md` — how the requirements will be met (types, endpoints, schemas, decisions)
   - `tasks.md` — ordered checklist; each task cites the requirement IDs it satisfies
3. Phases are built **in order** (00 → 07). Do not start a phase until the previous phase's
   `tasks.md` is fully checked and `./mvnw verify` is green.
4. Implement **one task at a time**: write the test first, make it pass, run the build, tick the
   checkbox in `tasks.md`, then stop and report. Do not batch several tasks unless asked.
5. If a spec is wrong, ambiguous or incomplete: **stop and propose a spec change** (show the diff
   to `requirements.md`/`design.md`) before touching code. Specs and code must never disagree.
6. Significant decisions get an ADR in `docs/adr/` (copy `0000-template.md`).

Slash commands for this workflow live in `.claude/commands/`:
`/spec-status`, `/spec-design`, `/spec-tasks`, `/spec-implement`, `/spec-review`.

## Non-negotiable rules

- `domain` packages are plain Java: no Spring, JPA, Jackson or Kafka imports. Enforced by ArchUnit.
- Adapters depend on application ports; the domain depends on nothing.
- Every production class has tests. Domain: pure JUnit 5. Application services: JUnit 5 + Mockito.
  Adapters: Spring slice tests or Testcontainers. No H2, no embedded Kafka — use Testcontainers.
- No task is done while `./mvnw verify` fails.
- Never commit secrets. Local credentials live in `docker-compose.yml` / `.env.example` only.
- Keep changes small and focused on the current task.

## Commands

```bash
./mvnw verify                       # build + all tests (Windows: mvnw.cmd verify)
./mvnw -pl dispatch-service test    # tests of one module
docker compose up -d                # local infra (Postgres, MongoDB, Kafka, Prometheus, Grafana)
docker compose --profile apps up -d --build   # infra + both services
```

## Git

- Conventional Commits: `feat(dispatch): ...`, `test(tracking): ...`, `docs(spec): ...`, `chore: ...`
- One branch + PR per task group; PR description follows `.github/pull_request_template.md`
  and lists the requirement IDs covered.

## Language

- Talk to the user in **Portuguese (pt-BR)**.
- Code, identifiers, commits, specs, ADRs and README are in **English**.
