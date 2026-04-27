# ADR 0006 — Split product, architecture, and agent-entry docs

- Status: Accepted
- Date: 2026-04-27

## Context and Problem Statement

A coding agent landing in this repo needs to learn three quite
different things: (1) what the service does and why, (2) how the code
is organized and what conventions apply, (3) where to start reading.
Stuffing all three into one file makes each section harder to find and
encourages drift (product changes get mixed in with architecture
edits).

## Decision Drivers

- Agents already read `AGENTS.md` first by convention.
- Glossary / ubiquitous language is product-level, not architecture.
- Architecture detail (layering rules, conventions) shouldn't bury the
  business context.
- Each document should have a single, defensible reason to change.

## Considered Options

1. **Single doc** (`AGENTS.md` containing everything).
2. **Two docs** (`AGENTS.md` + one of: `product.md` or
   `ARCHITECTURE.md`).
3. **Three-doc split with cross-links** —
   - `product.md` = product description + glossary,
   - `AGENTS.md` = entry point + short repo-map table + tech stack +
     invariants + link to ARCHITECTURE,
   - `ARCHITECTURE.md` = layering, full repo map, conventions,
     migration rules, naming.

## Decision Outcome

Chosen option: **Three-doc split** (option 3). Files cross-link:

- `AGENTS.md` includes `@product.md` (so product context is in the
  agent's first read) and links to `ARCHITECTURE.md`.
- `product.md` ends with a "See also" pointer to `ARCHITECTURE.md`,
  `docs/adr/`, `docs/specs/`.
- `ARCHITECTURE.md` references `product.md` for terminology and
  `docs/adr/` for rationale.

## Consequences

- Three files to keep coherent; agents must update the right one.
  Rule: if a change is about *what the service does*, edit
  `product.md`; if it's about *how code is organized*, edit
  `ARCHITECTURE.md` and write an ADR; the short map in `AGENTS.md`
  follows whichever underlying file changed.
- Glossary lives only in `product.md`. Other docs link to it; they do
  not redefine terms.
- `README.md` remains the human runbook (build/run/deploy commands)
  and is intentionally separate from the agent-oriented docs above.
