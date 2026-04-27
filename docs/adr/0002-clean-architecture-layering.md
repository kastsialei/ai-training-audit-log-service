# ADR 0002 — Clean architecture layering

- Status: Accepted
- Date: 2026-04-27

## Context and Problem Statement

The audit-log service is small today but has long-lived consumers
(compliance, security) and is expected to outlive several iterations of
its surrounding tech (web framework, ORM, transport). We need a layout
that protects domain rules from churn at the edges.

## Decision Drivers

- Domain logic (event invariants, immutability rules) must remain
  testable without Spring or a database.
- Persistence and HTTP are replaceable details.
- AGENTS.md already states: "Dependencies only point inward."

## Considered Options

1. **Clean architecture / hexagonal** — `domain → application →
   infrastructure → api`, dependencies inward.
2. **Layered MVC** — `controller → service → repository → entity`.
3. **Vertical slices** — feature-only, no horizontal layers.

## Decision Outcome

Chosen option: **Clean architecture**. Four layers:

- `domain/` — entities, value objects, domain services, invariants.
- `application/` — use cases and ports (interfaces) the domain needs.
- `infrastructure/` — adapters that implement application ports
  (persistence, external clients).
- `api/` — HTTP controllers and request/response DTOs.

Inward dependency rule is enforced by review (and by `ArchUnit` later if
drift becomes a problem).

## Consequences

- Domain types are pure Java with no framework annotations — *except*
  JPA, see ADR-0004.
- Adding a new feature touches every layer; folder discipline (ADR-0003)
  keeps it navigable.
- Repository interfaces (ports) are owned by `application/`, not
  `domain/` — `domain/` does not import `application/`.
