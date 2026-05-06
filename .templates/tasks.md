# Tasks: <feature name>

> Filled in after `design.md` is locked. Tasks turn the design into a sequence
> of reviewable, mergeable units of work.

- Requirements: <link to requirements.md>
- Design: <link to design.md>

## Ground rules

These apply to **every** task in this file.

1. **One commit per task.** A task ships as a single commit (and a single PR
   if PRs are used). If you find yourself writing "and also..." — split it.
2. **Size limit: 300–400 LOC of finished diff** (excluding generated files,
   lock files, fixtures, vendored code). If a task grows past this during
   implementation, **stop and split it** — do not merge a larger commit.
3. **Every task links back.** Reference the requirements user story (`US-N`)
   and the design section it implements. No orphan tasks.
4. **Definition of Done is testable.** Each DoD bullet must be verifiable —
   a passing test, an observable behavior, a checked artifact. "Looks good"
   is not a DoD.
5. **Dependencies are explicit.** A task lists upstream tasks it needs and is
   not started until those are merged. If two tasks have no dependency,
   they can run in parallel.
6. **Test-first inside the task.** Per repo convention: failing test → impl →
   green. The DoD reflects that — tests exist and pass.
7. **Tech lead / architect review before implementation starts.** Once this
   file is drafted, the lead reviews task breakdown, sizing, ordering, and
   DoD wording. Implementation begins only after sign-off. Reviewer signs
   off in the **Review** section below.
8. **Out-of-scope work goes back to the design or a follow-up task.** Don't
   silently expand a task during implementation. If the design is wrong,
   update `design.md` first.

## Task overview

Quick map for sequencing and parallelism. Keep in sync with the per-task
sections.

| ID | Title | Depends on | Refs | Est. LOC | Status |
|---|---|---|---|---|---|
| T-1 | <short title> | — | US-1, design §2 | <~150> | todo |
| T-2 | <short title> | T-1 | US-1, design §3 | <~250> | todo |
| T-3 | <short title> | T-1 | US-2, design §5 | <~200> | todo |

Status values: `todo` · `in-progress` · `in-review` · `done` · `blocked`.

---

## T-1: <short imperative title>

**Refs:** US-1 (requirements.md), design.md §<section>
**Depends on:** —
**Est. LOC:** <~N>

### Goal

<1–2 sentences: what this task delivers and why it exists as its own unit.>

### Scope

- <Concrete change 1 — file / module / behavior>
- <Concrete change 2>

### Out of scope

- <Thing a reviewer might expect here but belongs in T-N or a follow-up>

### Definition of Done

Each bullet must be checkable. Prefer "test X passes" over "feature works".

- [ ] <Failing test added first, now passes: `path/to/Test.java::method`>
- [ ] <Observable behavior: e.g. `POST /audit-events` returns 400 on null context>
- [ ] <Artifact: migration `V<n>__<name>.sql` applied cleanly on empty + populated DB>
- [ ] No new linter / formatter / type-check warnings
- [ ] Diff under 400 LOC (excluding generated)

### Notes / risks

<Anything the implementer should know that isn't in design.md — gotchas,
ordering with other tasks, rollback notes. Optional.>

---

## T-2: <short imperative title>

**Refs:** US-1, design.md §<section>
**Depends on:** T-1
**Est. LOC:** <~N>

### Goal
<...>

### Scope
- <...>

### Out of scope
- <...>

### Definition of Done
- [ ] <...>
- [ ] <...>

### Notes / risks
<...>

---

## Review

Filled in by the tech lead / architect before implementation starts.

- [ ] Each task references a user story and a design section
- [ ] Each task fits in one commit ≤ 400 LOC; oversized tasks were split
- [ ] DoD bullets are testable / observable, not subjective
- [ ] Dependencies form a DAG (no cycles), parallelizable tasks identified
- [ ] No task contains work outside `design.md`; new scope was added to design first
- [ ] Sequencing minimizes long-lived branches and merge conflicts

**Reviewer:** <name>
**Reviewed on:** <YYYY-MM-DD>
**Verdict:** approved / changes requested
**Notes:** <optional>
