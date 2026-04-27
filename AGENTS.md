# Audit Log Service

Internal service for immutable audit event storage. Required for compliance,
security, and observability. See [product.md](product.md) for full description.

## Tech stack
Java 21 · Spring Boot 3 · PostgreSQL 17 + Flyway · Docker · JUnit 5 + Testcontainers

## Quick refs

| Topic | Path / Command |
|---|---|
| Full conventions | [ARCHITECTURE.md](ARCHITECTURE.md) |
| Product glossary | [product.md](product.md) |
| Run locally | `docker compose -f deploy/docker-compose.yml up -d` |
| Format code | `mvn spotless:apply` |
| CI pipeline | [.github/workflows/ci.yml](.github/workflows/ci.yml) |

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
- **Layering.** Domain → Application → Infrastructure → API. Dependencies inward only.
- **Schema via migrations.** Never edit applied migrations.
- **UTC everywhere.** Server is source of truth for timestamps.
- **No silent failures.** Ingestion failures must be logged and observable.

For full naming conventions, cross-cutting concerns, and repo map → [ARCHITECTURE.md](ARCHITECTURE.md).