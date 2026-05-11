# Audit Log Service

Internal service for immutable audit event storage. Required for compliance,
security, and observability. See [product.md](product.md) for full description.

## Tech stack
Java 21 · Spring Boot 3 · PostgreSQL 17 + Flyway · Docker · JUnit 5 + Testcontainers

## Quick refs

| Topic | Path / Command                                             |
|---|------------------------------------------------------------|
| Full conventions | [ARCHITECTURE.md](ARCHITECTURE.md)                         |
| Product glossary | [product.md](product.md)                                   |
| Spec workflow (new features) | [.specs/WORKFLOW.md](.specs/WORKFLOW.md)   |
| Spec templates | [.templates/](.templates/)                                 |
| Run locally | `docker compose -f deploy/docker-compose.yml up -d`        |
| Format code | `mvn spotless:apply`                                       |
| CI pipeline | [.github/workflows/build.yml](.github/workflows/build.yml) |

## Where things live

| Concern | Path |
|---|---|
| Domain model (JPA entities) | `src/main/java/.../audit/domain/<feature>/` |
| Use cases / ports | `src/main/java/.../audit/application/<feature>/` |
| Persistence adapters | `src/main/java/.../audit/infrastructure/persistence/<feature>/` |
| HTTP API (controllers, DTOs) | `src/main/java/.../audit/api/<feature>/` |
| DB migrations | `src/main/resources/db/migration/V<n>__<snake>.sql` |
| Unit tests (`*Test.java`) | `src/test/java/...` mirrors main |
| Integration tests (`*IT.java`) | `src/test/java/...` alongside units |

## Key rules

- **Simplicity first.** Minimum code that solves the problem. Nothing speculative.
- **Surgical changes.** Touch only what you must. Don't "improve" adjacent code.
- **Goal-driven.** Transform tasks into verifiable goals. Test before, test after.
- **Commit early, commit often.** Small commits with clear messages. Don't bundle unrelated changes. Maximum 5-7 files with 300-500 lines changed per commit.
- **Test-driven.** Write tests first. Make them fail. Then implement. Then make them pass.
- **Layering.** Domain → Application → Infrastructure → API. Dependencies inward only.
- **Schema via migrations.** Never edit applied migrations.
- **UTC everywhere.** Server is source of truth for timestamps.
- **No silent failures.** Ingestion failures must be logged and observable.
- **Error contract.** All HTTP errors (4xx and 5xx) return RFC 7807
  `ProblemDetail` with `Content-Type: application/problem+json` and the
  fields `type`, `title`, `status`, `detail`, `instance`. Bad client
  input is 4xx, never 5xx. Every response carries `X-Correlation-Id`.
- **Cross-task contracts.** When a feature spans more than two tasks,
  pin the shared types, ports, packages, and exception structure in
  `.specs/<feature>/glossary.md` before per-task implementation
  starts. Plans cite the glossary; the glossary wins on conflict.

For full naming conventions, cross-cutting concerns, and repo map → [ARCHITECTURE.md](ARCHITECTURE.md).

## New feature? Write a spec first

For any **new endpoint, subsystem, or contract/schema change**, follow
the spec workflow before writing code: draft `requirements.md` →
`design.md` → `tasks.md` under `.specs/<feature>/`, starting from
[`.templates/`](.templates/). Skip for bug fixes, refactors, and trivial
edits.

- **The spec is the source of truth.** Fix gaps in the spec first, code
  second. Out-of-scope work goes back to requirements/design — never
  silently expand a task.
- **Don't hallucinate.** When uncertain, ask. Unresolved items live in
  `requirements.md` → *Open questions* until answered.
- **Acceptance criteria use EARS** (Given/When/Then is acceptable).
- **List endpoints use a deterministic sort with a unique tiebreaker**
  (e.g. `(recorded_at DESC, id DESC)`).

Full rules, EARS shapes, and project invariants → [.specs/WORKFLOW.md](.specs/WORKFLOW.md).