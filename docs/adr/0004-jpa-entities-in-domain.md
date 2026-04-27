# ADR 0004 — JPA entities live in the domain layer

- Status: Accepted
- Date: 2026-04-27

## Context and Problem Statement

ADR-0002 establishes inward-only dependencies: `domain` depends on
nothing. The textbook clean-architecture move is then to keep
JPA-annotated entities in `infrastructure/persistence/` as a separate
type, with a mapper between them and pure domain entities. That
introduces a parallel object hierarchy and per-feature mapper code.

The audit-log service is **append-only**: events are written once and
never updated. The domain logic surrounding an event is small (validate,
stamp `recorded-at`, persist). The cost of a parallel model is
disproportionate to the benefit.

## Decision Drivers

- "Simplicity First" invariant from AGENTS.md — minimum code that solves
  the problem.
- Append-only means the JPA lifecycle (dirty checking, cascade,
  optimistic locking) is mostly inert; ORM mismatch risks are low.
- Dual-model boilerplate is the most common driver of "I'll just put
  business logic in the persistence model" drift.

## Considered Options

1. **Strict separation.** Pure POJO domain + JPA entity + mapper.
2. **Pragmatic single model.** JPA-annotated classes live in `domain/`.
3. **No JPA at all.** Hand-rolled `JdbcClient` repositories.

## Decision Outcome

Chosen option: **Pragmatic single model**. JPA `@Entity` classes live
in `domain/<feature>/` and are also the domain entities. The domain
imports `jakarta.persistence`.

This deliberately bends the inward-only rule (ADR-0002) for one
dependency: `jakarta.persistence`. No other framework dependencies
(`spring-*`, `jakarta.servlet`, etc.) are permitted in `domain/`.

## Consequences

- `domain/` is not framework-free. Document this exception in
  `ARCHITECTURE.md` so reviewers know the boundary.
- If we ever migrate off JPA (e.g. to JdbcClient for write-path
  performance), the change is contained: drop the annotations, add
  hand-written mappers in `infrastructure/persistence/`.
- Domain unit tests still run without Spring — JPA annotations are
  metadata, not runtime behaviour, when the entity is `new`'d directly.
- Schema is owned by Flyway migrations, not Hibernate DDL. Tests run
  with `ddl-auto=validate` to catch drift between entities and
  migrations.
