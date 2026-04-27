# ADR 0001 — Record architecture decisions

- Status: Accepted
- Date: 2026-04-27

## Context and Problem Statement

We are scaffolding the audit-log service and making decisions whose
rationale will not be obvious from the code (clean-architecture layering,
package layout, testing convention, doc structure). Without a written
record, future contributors and AI agents will see *what* we did but not
*why*, and may "improve" the structure away from its constraints.

## Decision Drivers

- AI agents need explicit rationale to make consistent extensions.
- Decisions made now (e.g. JPA in `domain/`) bend a stated invariant
  (clean architecture) and must be defensible.
- Lightweight overhead — we want decisions captured, not a process.

## Considered Options

1. **MADR template** — short, structured: context, drivers, options,
   decision, consequences.
2. **Nygard template** — minimal: context, decision, status,
   consequences.
3. **No ADRs** — rely on commit messages and `ARCHITECTURE.md`.

## Decision Outcome

Chosen option: **MADR template**. Lives in `docs/adr/`, filenames
`NNNN-kebab-title.md`, monotonically numbered.

## Consequences

- Every architectural decision must be paired with an ADR.
- `ARCHITECTURE.md` describes the *current* state; ADRs explain *how we
  got here*.
- ADRs are immutable once accepted — supersession is via a new ADR that
  references and supersedes the prior one.
