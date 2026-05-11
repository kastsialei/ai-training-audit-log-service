# Spec workflow

Use this workflow whenever you introduce a **new feature**: a new
endpoint, a new subsystem, or any contract/schema change. Skip it for
bug fixes, refactors with no contract change, and trivial edits — those
go straight to code.

## Layout

```
.specs/<feature>/         (kebab-case folder name)
  requirements.md  — what & why, user stories, acceptance criteria, scope
  design.md        — how, contract, data, edge cases, integration
  tasks.md         — sequenced, independently mergeable units of work
```

Start each file from the matching template in
[`../.templates/`](../.templates/). English only.

## Phases (run in order)

1. **Requirements.** Problem → user stories → acceptance criteria →
   out of scope → open questions. No tech choices, no API shape, no
   code.
2. **Design.** Resolve every open question. Public surface, data model,
   validation, invariants, edge cases, integration, risks. Begin only
   after requirements is locked.
3. **Tasks.** Decompose the design into mergeable units. Each task
   carries: goal, scope, refs to `US-N` and the design section, a
   testable DoD, ≤ 300–400 LOC. Tech-lead sign-off before implementation
   starts.

**The spec is the source of truth.** When code and spec diverge, update
the spec first, the code second. Out-of-scope work goes back to
requirements / design — never silently expand a task.

## Acceptance criteria — EARS style

Each AC is one observable, testable statement. Use one of these shapes:

- **Ubiquitous:** *The system shall &lt;behavior&gt;.*
- **Event-driven:** *When &lt;trigger&gt;, the system shall &lt;behavior&gt;.*
- **State-driven:** *While &lt;state&gt;, the system shall &lt;behavior&gt;.*
- **Unwanted:** *If &lt;unwanted condition&gt;, then the system shall &lt;behavior&gt;.*
- **Optional:** *Where &lt;feature is included&gt;, the system shall &lt;behavior&gt;.*

The Given/When/Then form used in the templates maps onto event-driven
EARS and is acceptable. One outcome per bullet.

## Don't hallucinate — ask

Questions are **not mandatory**, but **do not fill grey areas with
plausible-sounding guesses**. A new feature usually surfaces 4–7
clarifying questions — ask them up front. Anything still unresolved
goes into `requirements.md` → *Open questions* with an owner and the
phase by which it must be answered. A guess silently encoded into a
spec costs ten times more to unwind from design or code later.

## Project invariants every spec must reflect

These are global, not feature-specific. If a spec contradicts one,
justify it in *Design → Alternatives considered* or surface it in
*Open questions*.

- **Deterministic sort.** Any list endpoint sorts by a primary column
  plus a unique tiebreaker (e.g. `(recorded_at DESC, id DESC)`) so the
  order is total under concurrent writes.
- **Pagination.** Cursor-based; no offsets; no total counts unless
  explicitly justified.
- **Errors.** RFC 7807 `application/problem+json` for all 4xx.
- **Correlation.** `X-Correlation-Id` echoed (UUID generated if absent),
  bound to MDC for the request lifetime.
- **Time.** ISO-8601 with offset on the wire; UTC server-side.

## Naming & cross-references

- Feature folder: kebab-case (`.specs/query-api/`, not `QueryApi`).
- IDs: user stories `US-1, US-2, …`; tasks `T-1, T-2, …`.
- Between docs, link with markdown anchors, not section numbers — edits
  won't silently break references.

## Reference

A worked example: [`.specs/query-api/`](query-api/) (requirements,
design, tasks).
