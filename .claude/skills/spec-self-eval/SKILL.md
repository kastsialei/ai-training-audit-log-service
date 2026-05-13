---
name: spec-self-eval
description: Self-evaluate a feature spec against the bundled review checklist (references/REVIEW.md) and the project's invariant docs (AGENTS.md, ARCHITECTURE.md, .specs/WORKFLOW.md). Writes a dated review report into the feature's .specs/<feature>/reviews/ folder. Use this skill whenever the user asks to "review the spec", "evaluate requirements/design/tasks", "check the spec is ready", "run the spec review checklist", "self-review my feature spec", or any variation — and also proactively after meaningful edits to a feature's requirements.md, design.md, or tasks.md, both at the requirements+design checkpoint and at the requirements+design+tasks checkpoint defined in WORKFLOW.md. This skill OWNS spec evaluation — do not improvise a freeform review; load this skill so the report follows the project's PASS/WEAK/FAIL contract and lands in the right place.
---

# Spec self-eval

Evaluate a single feature spec under `.specs/<feature>/` against the
bundled review checklist ([`references/REVIEW.md`](references/REVIEW.md))
and the project's invariants (`AGENTS.md`, `ARCHITECTURE.md`,
`.specs/WORKFLOW.md`). Produce a dated report inside
`.specs/<feature>/reviews/`.

This is the project's two-gate review (per `WORKFLOW.md`):

- **Gate 1** — after `requirements.md` + `design.md` are drafted,
  before `tasks.md` is written. Sections **A, C, D, E** of
  `references/REVIEW.md` apply. Section B is skipped.
- **Gate 2** — after `tasks.md` is drafted, before tech-lead sign-off
  and implementation. **All sections (A, B, C, D, E)** apply.

The aim is to surface grey areas, vague language, missing edge cases,
and architecture-rule violations *before* they propagate into code,
where they cost ten times more to fix.

## Inputs you need

Before reviewing, gather these. Read them in parallel — they're
independent.

1. **The checklist** — [`references/REVIEW.md`](references/REVIEW.md),
   bundled with this skill. The PASS/WEAK/FAIL contract and section
   definitions live here; treat it as the source of truth for what
   each section checks.
2. **The spec under review** — `.specs/<feature>/requirements.md`,
   `.specs/<feature>/design.md`, and (if present) `.specs/<feature>/tasks.md`.
   If a `glossary.md` or `_delta.md` exists in the feature folder, read
   them too — they may resolve open questions cited by the spec.
3. **The project invariants** — `.specs/WORKFLOW.md` (EARS shapes,
   ask-don't-hallucinate, deterministic sort, cursor pagination, RFC
   7807, X-Correlation-Id, UTC), the project's `AGENTS.md` (key rules:
   layering, simplicity, migrations append-only, error contract,
   cross-task glossary requirement), and `ARCHITECTURE.md` (long-form
   layering, naming, package organization, cross-cutting conventions,
   migration rules).

If any of `requirements.md`/`design.md` is missing, the spec is too
early for this review — report that and stop. (Tasks-only or
design-only specs cannot be evaluated meaningfully.)

## Phase detection

Decide which gate applies by what's on disk inside the feature folder:

- `requirements.md` + `design.md` present, `tasks.md` **absent or
  empty/placeholder** → **Gate 1**. Apply A, C, D, E. Mark B as
  *Skipped (Gate 1).*
- `requirements.md` + `design.md` + non-trivial `tasks.md` present →
  **Gate 2**. Apply A, B, C, D, E.

If the user explicitly names a gate ("run the post-tasks review",
"just the architecture pass"), honor that — but state in the report
which gate was applied and why.

## How to evaluate each section

Anchor every section in the bullet-list checks under that section's
heading in `references/REVIEW.md` — do not invent extra criteria. For
each section emit exactly one block, in the form that file requires:

```
## <Section name> — PASS | WEAK | FAIL
Evidence: <one sentence pointing at the specific bullet or naming the gap>
```

Rules of thumb:

- **PASS** — every check in the section is satisfied.
- **WEAK** — overall passes but at least one item is borderline. Flag
  it; the spec is still movable, but the author should fix it.
- **FAIL** — at least one check is blocking. The spec is not ready
  for the next phase.

Cite by file + heading (`requirements.md → US-2 AC #3`,
`design.md → §6 "Cursor format"`), not by line number — line numbers
rot the moment the file is edited.

End the body with one final line, exactly as `references/REVIEW.md`
requires:

```
Verdict: ready | fix-then-reready | blocked
```

Map verdicts conservatively:

- All sections PASS → `ready`.
- Any section WEAK, none FAIL → `fix-then-reready`.
- Any section FAIL → `blocked`.

### Cross-cutting checks (do not skip)

While walking the five sections, also verify the spec reflects the
project invariants. A violation belongs in **E (architecture)** if it
is structural and in **D (edge cases)** if it is behavioral. The
invariants to check, distilled from `WORKFLOW.md` and `AGENTS.md`:

- Deterministic sort with a **unique tiebreaker** on list endpoints
  (e.g. `(recorded_at DESC, id DESC)`).
- **Cursor pagination**; no offsets; no total counts unless explicitly
  justified in *Alternatives considered*.
- **RFC 7807** `application/problem+json` for every 4xx, with `type`,
  `title`, `status`, `detail`, `instance`. Bad client input is never
  5xx.
- **`X-Correlation-Id`** echoed (UUID generated if absent) and bound
  to MDC for the request lifetime.
- **UTC** server-side; ISO-8601 with offset on the wire.
- **Layering** — Domain → Application → Infrastructure → API,
  dependencies inward only (per `ARCHITECTURE.md`).
- **Migrations** — append-only Flyway; never edit applied migrations.
- **Cross-task contracts** — for features spanning more than two
  tasks, shared types/ports/exception structure live in
  `.specs/<feature>/glossary.md` and tasks cite it (`AGENTS.md → Key
  rules`).
- **Acceptance criteria** in EARS or Given/When/Then form, one
  observable outcome per bullet, no vague adjectives.

Each invariant that is required by the feature but missing or
contradicted is a finding. Quote where you found the gap.

### Section-specific guidance

- **A. Acceptance criteria.** Every AC must be observable — a
  developer can write a failing test before the code exists. Flag
  vague language ("fast", "reasonable", "robust", "user-friendly",
  "etc."). Every AC ties back to a user story id (`US-N`).

- **B. Tasks & DoD.** (Gate 2 only.) Every task references its user
  story and the design section it implements; every DoD bullet maps
  to a passing test, an observable behavior, or a checked artifact;
  dependencies form a DAG; each task fits ≤ 300–400 LOC; no orphan
  tasks (work not justified by requirements/design) and no orphan
  ACs (AC with no task delivering it).

- **C. Relevance & scope.** Spec stays focused on the feature; no
  scope creep; **Out of scope** is non-trivial and names what a
  reviewer would otherwise expect; every **Open question** in
  requirements is either resolved in design *or* carries an owner
  and a resolution phase.

- **D. Edge cases & failure modes.** Validation failures, malformed
  input, conflicts, boundary conditions (empty result, max size,
  page-of-one, last page), failure modes (partial failure, downstream
  unavailable, concurrent writes, replay/duplicate, clock skew). Each
  edge has a *specified* behavior, not an implied one.

- **E. Architecture quality.** No god types, no leaky abstractions,
  no domain logic in the HTTP layer, no circular dependencies.
  Abstractions are right-sized — neither premature generality nor
  copy-paste across layers. Each cross-module boundary names the
  port, the owner, and the failure contract. Project layering
  respected.

If you find issues that span sections, place them in the most
specific section and reference them from the other; do not
double-count toward the section verdict.

## Where to write the report

1. Ensure the directory `.specs/<feature>/reviews/` exists; create
   it if it does not (no other files in `reviews/` are touched).
2. Pick the filename based on what's already there:
   - If no review exists for **today** → `review-YYYY-MM-DD.md`.
   - If a review already exists for today →
     `review-YYYY-MM-DD-HHMM.md`, using local time in 24-hour form.
     Re-runs the same day always produce a *new* file; never
     overwrite a prior review.
3. Use the project's local date for `YYYY-MM-DD` (in this repo:
   the user's local timezone — match it to the `currentDate` value
   surfaced in the session's environment, not UTC, so the filename
   matches the author's wall clock).

Write the report — *do not* append to a prior review file, and do not
touch `requirements.md`/`design.md`/`tasks.md`. This skill is
read-only against the spec; the only file it creates is the report.

## Report format

Use this template verbatim — it's the contract a future reader (and
the author iterating on the spec) relies on.

```markdown
# Spec self-eval — <feature>

- **Gate:** Gate 1 (requirements + design) | Gate 2 (requirements + design + tasks)
- **Date:** YYYY-MM-DD HH:MM <timezone>
- **Reviewer:** spec-self-eval skill
- **Spec snapshot:**
  - requirements.md — present
  - design.md — present
  - tasks.md — present | absent
  - glossary.md — present | absent
  - HEAD commit (if a git working tree): <sha-or-"uncommitted-changes">

## A. Acceptance criteria — PASS | WEAK | FAIL
Evidence: <one sentence pointing at the specific bullet or naming the gap>

## B. Tasks & Definition of Done — PASS | WEAK | FAIL | Skipped (Gate 1)
Evidence: <one sentence — or "Tasks not yet drafted" for Gate 1>

## C. Relevance & scope — PASS | WEAK | FAIL
Evidence: <one sentence>

## D. Edge cases & failure modes — PASS | WEAK | FAIL
Evidence: <one sentence>

## E. Architecture quality — PASS | WEAK | FAIL
Evidence: <one sentence>

## Cross-cutting invariants
Check each project invariant that is in scope for this feature. Each
bullet is OK | MISSING | VIOLATED with a one-line citation. Omit
invariants that genuinely do not apply (e.g. correlation ID for a
feature with no HTTP surface), and say why under "Not applicable".

- Deterministic sort with tiebreaker — <status>: <citation>
- Cursor pagination, no offsets/totals — <status>: <citation>
- RFC 7807 errors with type/title/status/detail/instance — <status>: <citation>
- X-Correlation-Id echo + MDC binding — <status>: <citation>
- UTC server-side, ISO-8601 with offset on the wire — <status>: <citation>
- Layering (Domain → Application → Infra → API) — <status>: <citation>
- Migrations append-only, never edit applied — <status>: <citation>
- Cross-task glossary present when feature spans > 2 tasks — <status>: <citation>
- ACs in EARS / GWT, one observable outcome per bullet — <status>: <citation>

Not applicable: <invariants explicitly excluded, with one-line reason>

## Findings
Numbered list. One finding per logical issue. For each:

1. **<short title>** — section <A|B|C|D|E>, severity **FAIL | WEAK**.
   - **Where:** <file → heading>
   - **What's wrong:** <one or two sentences quoting or paraphrasing the spec>
   - **Why it matters:** <impact if shipped: which AC fails, which invariant breaks, which test cannot be written>
   - **Suggested fix:** <concrete edit in the spec, not in code>

If a section is PASS and you have nothing material to add, omit it
here — do not pad.

## Verdict: ready | fix-then-reready | blocked
```

Keep findings concrete and surgical. "AC #2 is vague" is not a
finding; "US-3 AC #2 uses 'robust' with no observable trigger" is.
Quote the offending phrase when it helps the author find it.

## Worked example (excerpt)

What a good evidence line looks like:

```
## A. Acceptance criteria — WEAK
Evidence: US-3 AC #2 ("cursor handling is robust") names no observable outcome.
```

What a bad one looks like (do not emit this):

```
## A. Acceptance criteria — WEAK
Evidence: Some ACs could be clearer.
```

The bad version cannot be acted on. The good version sends the
author straight to the line and tells them exactly what's wrong.

## What this skill must not do

- **Do not edit the spec.** Only write the report file. The author
  closes the loop by editing `requirements.md` / `design.md` /
  `tasks.md` based on the findings, then re-runs this skill.
- **Do not invent criteria not in `references/REVIEW.md`.** If you
  spot an issue the checklist doesn't anchor, it still goes in
  **Findings**, but the five section verdicts must come from the
  checklist's bullets only.
- **Do not file findings against deliberately out-of-scope items.**
  The *Out of scope* section is a feature of the spec, not a bug.
  Findings against scope decisions belong in **C** only if scope is
  internally inconsistent (e.g. a user story implicitly requires a
  feature that's marked out of scope).
- **Do not assign work to people.** The skill names the gap and a
  suggested fix; ownership and priority are the author's call.

## Why each step matters

The bundled checklist exists because freeform spec reviews drift.
Without an anchored checklist, a reviewer focuses on whatever they
noticed first and silently skips the rest. With it, the report is
comparable across
features and across reviews — the same five sections, the same
verdict vocabulary, the same expected evidence shape. That
comparability is what makes the gate useful: a reader two months
later, or a different reviewer, can pick up the report cold and know
exactly which parts of the spec passed and which need work.

Cross-checking against `AGENTS.md` and `ARCHITECTURE.md` is the
mechanism that keeps individual specs honest with the project's
global rules. A single spec author cannot hold every invariant in
their head; the skill's job is to be the third pair of eyes that
notices when "we'll return totals" silently violates the
cursor-only rule, or when a new feature drops error responses
outside RFC 7807. Catching that here costs minutes; catching it in
code review after the implementation lands costs hours; catching it
after merge can cost a follow-up migration.

Writing into `.specs/<feature>/reviews/` instead of pasting into
chat leaves a durable, dated trail. The author can see how the
spec evolved across iterations, and a future reader can verify the
spec was actually reviewed at each gate.
