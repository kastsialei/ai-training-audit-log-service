# ADR 0003 — Package by feature inside each layer

- Status: Accepted
- Date: 2026-04-27

## Context and Problem Statement

ADR-0002 fixes the four horizontal layers (`domain`, `application`,
`infrastructure`, `api`). Within each layer we still need a sub-layout.
A flat layer with all features mixed becomes unscannable as features
grow; a strict feature-first layout fights Maven/Spring conventions.

## Decision Drivers

- Agents should be able to add a new feature without negotiating layout
  decisions every time.
- `grep` for one feature should return cohesive results.
- Cross-cutting types (e.g. shared value objects) need a home that
  doesn't bloat by default.

## Considered Options

1. **Flat per layer** — `domain/AuditEvent.java`, `domain/Actor.java`.
2. **Feature inside layer** — `domain/ingestion/AuditEvent.java`,
   `domain/query/EventFilter.java`. (`shared/` only when needed.)
3. **Feature first, layer inside** —
   `feature/ingestion/{domain,application,api}/...`.

## Decision Outcome

Chosen option: **Feature inside layer**. Each horizontal layer contains
feature subpackages (`ingestion/`, `query/`, ...). A `shared/`
subpackage is created in a layer **only when at least two features
depend on a type** — never speculatively.

## Consequences

- New feature `X` adds (typically) `domain/X/`, `application/X/`,
  `api/X/`, `infrastructure/persistence/X/`.
- Tests mirror this layout: `src/test/java/.../domain/X/`,
  `src/test/java/.../application/X/`, etc.
- Promotion to `shared/` is an explicit step recorded in the PR — not a
  silent reorganization.
