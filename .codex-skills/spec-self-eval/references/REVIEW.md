---
name: spec-review
description: Pre-planning and pre-implementation review checklist for feature specs. Five sections (AC, tasks, scope, edge cases, architecture) each scored PASS / WEAK / FAIL with one-line evidence.
applies-to: ".specs/**/{requirements,design,tasks}.md"
---

# Spec review

Use this checklist to **evaluate a feature spec before planning or
implementation starts**. Run it twice:

1. After `requirements.md` + `design.md` are drafted, before `tasks.md`
   is decomposed. Sections A, C, D, E apply.
2. After `tasks.md` is drafted, before tech-lead sign-off. All sections
   apply.

The aim is to catch grey areas, vague language, and missing edge cases
*before* they propagate into code, where they cost ten times more to
fix.

## How to output the review

For each section, emit exactly one block:

```
## <Section name> — PASS | WEAK | FAIL
Evidence: <one sentence pointing at the specific bullet or naming the gap>
```

- **PASS** — every check in the section is satisfied.
- **WEAK** — overall passes but at least one item is borderline. Flag it.
- **FAIL** — at least one check is blocking. Spec is not ready.

End with one final line:

```
Verdict: ready | fix-then-reready | blocked
```

Cite by file + heading (e.g. `requirements.md → US-2 AC #3`), not by
line number — line numbers rot.

---

## A. Acceptance criteria

- Every AC is **observable and testable** — a developer can write a
  test that fails before implementation and passes after.
- ACs are in **EARS** or Given/When/Then form — one outcome per bullet.
- **No vague language** ("fast", "reasonable", "robust", "user-friendly",
  "etc."). Each AC names a concrete trigger, condition, and outcome.
- Every AC ties back to a user story.

## B. Tasks & Definition of Done

(Skip on the requirements-plus-design pass — applies once `tasks.md`
exists.)

- Every task has a DoD made of **testable bullets** — each maps to a
  passing test, an observable behavior, or a checked artifact.
- **Dependencies between tasks are explicit** and form a DAG (no
  cycles, no implicit ordering).
- Tasks are **consistent**: no two tasks define the same thing
  differently; no task contradicts requirements or design.
- Each task references its user story (`US-N`) and the design section
  it implements.
- Each task fits ≤ 300–400 LOC; oversized tasks are split.
- No orphan tasks (work not justified by requirements/design); no
  orphan ACs (AC with no task that delivers it).

## C. Relevance & scope

- The spec stays **focused on the feature** — no tangential subsystems
  pulled in.
- **No scope creep**: every decision traces to a user story or a
  project invariant.
- **Clear in/out boundaries**: `Out of scope` is non-trivial and names
  the things a reviewer would otherwise expect to find.
- All `Open questions` from `requirements.md` are either resolved in
  `design.md` or carry an owner and a phase by which they'll be
  resolved. No open question is silently dropped.

## D. Edge cases & failure modes

- **Error states covered**: validation failures, missing/malformed
  input, conflicts — each named with its response shape.
- **Boundary conditions** addressed: empty result, max size, min/max
  limits, page-of-one, last-page behavior.
- **Failure modes** considered: partial failure, downstream
  unavailable, concurrent writes, replay/duplicate, clock skew.
- Behavior at each edge is **specified, not implied**.

## E. Architecture quality

- **No code smells** in the proposed design: no god types, no leaky
  abstractions, no circular dependencies, no domain logic in the HTTP
  layer.
- **Abstractions are right-sized** — not over-engineered (premature
  generality, unused indirection) and not under-engineered (logic
  copy-pasted across layers).
- **Integration points are clean**: each cross-module boundary names
  the port, the owner, and the failure contract.
- Project layering respected (Domain → Application → Infrastructure
  → API, dependencies inward only — see AGENTS.md *Key rules*).

---

## Cross-cutting reminder

While reviewing, also verify the spec reflects the project invariants
listed in [`WORKFLOW.md`](WORKFLOW.md) — deterministic sort with
tiebreaker, cursor pagination, RFC 7807 errors, `X-Correlation-Id`,
UTC. A violation belongs in **E (architecture)** if structural, or
**D (edge cases)** if behavioral.

## Example output

```
## A. Acceptance criteria — WEAK
Evidence: US-3 AC #2 ("cursor handling is robust") names no observable outcome.

## B. Tasks & Definition of Done — PASS
Evidence: All 11 tasks have testable DoD bullets; dependency graph is a DAG.

## C. Relevance & scope — PASS
Evidence: Out-of-scope section names 12 deferred concerns with reasons.

## D. Edge cases & failure modes — FAIL
Evidence: Concurrent ingestion during pagination is named in US-3 but not addressed in design §5.

## E. Architecture quality — PASS
Evidence: Layering respected; ports named at each boundary; no premature generality.

Verdict: fix-then-reready
```
