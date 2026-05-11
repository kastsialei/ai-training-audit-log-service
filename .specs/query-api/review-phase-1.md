---
name: query-api-review-phase-1
description: Phase 1 spec review (requirements + design, before tasks decomposition) for the query-api feature. Sections A, C, D, E per .specs/REVIEW.md.
applies-to: ".specs/query-api/{requirements,design,glossary}.md"
phase: 1
verdict: fix-then-reready
---

# Spec review — query-api (Phase 1: requirements + design)

Scope: `.specs/query-api/requirements.md`, `.specs/query-api/design.md`,
`.specs/query-api/glossary.md`. Tasks deliberately excluded (Phase 2).
Checklist: [`.specs/REVIEW.md`](../REVIEW.md).

## A. Acceptance criteria — PASS
Evidence: Every AC across `requirements.md → US-1..US-4` names a concrete trigger, condition, and observable outcome (status code, body shape, sort key, error `type`); each ties back to its user story and is testable.

## C. Relevance & scope — PASS
Evidence: `requirements.md → Out of scope` enumerates 12 non-trivial exclusions with reasons (auth, totals, alt sort, schema, bulk export, SLOs, observability, versioning, cursor TTL, 5xx shape, unknown params), and every `requirements.md → Open questions` item is either resolved in `design.md` (indexes in §3, cursor signing in §6/§9/§12) or carries an owner+phase (multi-value `outcome`, window size/rate limiting).

## D. Edge cases & failure modes — WEAK
Evidence: `design.md → §6 Edge cases` covers empty/null/missing input, `from==to`, missing offsets, concurrent ingestion, cursor tampering, and limit edges with explicit responses; however `requirements.md → US-2 AC #2` mandates "ISO-8601 with timezone information" while `design.md → §6 "from/to corner cases"` and `§4` only describe rejection for *missing offset* — `Z`-suffix handling and behavior on a `from`/`to` that parses as a local-date-time vs an instant are not explicitly nailed down, and the oversize-`context` path is acknowledged to surface as a `500 internal-error` while `requirements.md → US-4 AC #4` says bad client input must never surface as `500` (here it's server-driven, but the 5xx response shape is itself listed as out-of-scope, leaving the behavior under-specified).

## E. Architecture quality — PASS
Evidence: `design.md → §3 Entity placement` correctly promotes `AuditEvent`/`Outcome` to `domain/shared/` per ADR-0003 (no cross-feature reach-in); `glossary.md → §3` names every port (`EventReader`), use case, codec, and adapter with owners; layering is inward-only (API → application → domain, infra implements ports); the `≥1 substantive filter` rule is correctly placed in the use case rather than the DTO (`design.md → §4 "Where rules live"`) and `glossary.md → §7` pins use-case execution order so error precedence is deterministic.

Verdict: fix-then-reready

---

## Findings

- **[D] Timestamp-format edge.** `requirements.md → US-2 AC #2` requires "ISO-8601 with timezone information" but neither `design.md → §4` (`invalid-timestamp` row) nor `§6 "from/to corner cases"` calls out the `Z` suffix explicitly nor what happens for a string Spring's binder *would* accept as `OffsetDateTime` versus reject. Fix: add a bullet in `§6` listing accepted shapes (`...+00:00`, `...Z`) and one rejection case beyond the bare local datetime.
- **[D] Oversize `context` → 500 conflicts with US-4 framing.** `design.md → §6 "Oversize / pathological responses"` accepts a `500 internal-error` for oversized payloads, while `requirements.md → US-4 AC #4` reads as "bad *client* input must never surface as `500`." These don't strictly contradict (server-side serialization isn't client input), but the boundary is implicit. Fix: in `§6` or `§8`, state the rule as "server-side failures may 5xx; *bad client request* never 5xx" so the contract is unambiguous.
- **[D] 5xx response shape vs RFC 7807 prerequisite.** `requirements.md → Out of scope` says "5xx response shape ... framework default," but `design.md → §7 Prerequisites #1` says the advice covers a "safe catch-all to RFC 7807 `ProblemDetail`" — meaning 5xx will in fact be RFC 7807 in practice. Fix: reconcile — either tighten the out-of-scope wording ("5xx body fields stable but not guaranteed feature-namespaced") or note the prerequisite ships a 5xx shape by side effect.
- **[D] Cursor with `from`/`to` boundaries.** No edge case spells out behavior when a cursor's `rt` equals `from` (inclusive) on a refetch — the strict-less-than keyset is fine, but the interaction with the `from`-inclusive contract isn't shown. Fix: add a one-liner in `design.md → §5 invariant 2` confirming the keyset predicate is applied *after* the `from`-inclusive filter so the inclusive bound is honored only on page 1.
- **[D] Pagination "page-stability" caveat.** `design.md → §5 invariant 2` says "*at or before the cursor's `recorded_at`*" — but ingestion uses `now()`, so older timestamps cannot in fact be inserted; the caveat is harmless but the wording "events that existed at or before" is broader than what the system guarantees. Fix: tighten to "events with `recorded_at <= cursor.rt`" and explicitly state that *backdated* writes are not a supported ingestion mode.
- **[A] US-3 AC #5 "tampered cursor" outcome ambiguity.** AC says tampered cursor → 400 with no internal leak; `design.md → §6 "Cursor failure modes"` splits tampered into either `invalid-cursor` *or* `cursor-filter-mismatch`. Which `type` a caller sees is decidable, but the AC implies one outcome. Fix: amend `US-3 AC #5` to say "either `invalid-cursor` or `cursor-filter-mismatch`, depending on structural validity."
- **[A] US-4 AC #4 scoping.** AC mixes two concerns (RFC 7807 shape + ingestion's existing 500 → 400 migration) into one bullet. Testable, but conflates the prerequisite slice's behavior with this slice's. Fix: split into two ACs — one about ingestion's status code change, one about the shared shape.
- **[C] Open question on cursor signing is "resolved" but parked.** `requirements.md → Open questions` "cursor signing — owner: implementer; needed by: design phase" — `design.md → §6` resolves it ("unsigned + fingerprint, security review may upgrade") but `§12` re-lists it as open against pre-prod rollout. Status is consistent if read carefully, but the owner ownership transfer (implementer → security review) is implicit. Fix: in `requirements.md → Open questions`, mark "resolved in design §6; tracked for pre-prod in design §12 under security review."
- **[E] `RequestedLimit` borderline over-engineering.** `glossary.md → §2` introduces a `RequestedLimit(int value)` wrapper purely to "keep `QueryCriteria` clean." A nullable/boxed `Integer` passed alongside `QueryCriteria` would do the same. Not blocking, but flag. Fix: either justify in `design.md → §9 Alternatives considered` or drop the wrapper.
- **[E] Snake_case parameter binding left "T-9 picks."** `glossary.md → §6.1` lists three plausible mechanisms and defers to T-9. For a frozen glossary, that's a deferred decision on a shared contract. Fix: pin the mechanism now (likely `@ConstructorBinding` with explicit `@RequestParam` aliasing on each component) since T-10 validation depends on the binding mode.
- **[E] `InvalidQueryException` field reporting.** `glossary.md → §4` exposes `Optional<String> field()` only for `BLANK_FILTER`, but `design.md → §4` examples include "actor must not be blank" — a static-message-by-kind constraint conflicts with naming the specific field in `detail`. Fix: clarify whether `detail` for `BLANK_FILTER` is the static "a filter must not be blank" (and `field` is metadata) or whether the field name is interpolated (which contradicts the "messages are static, never include user-supplied input" rule).
- **[E] `INVALID_LIMIT` routing.** `glossary.md → §5` notes `INVALID_LIMIT` is field-routed by a handler switch on `MethodArgumentNotValidException`. Field-name string matching in a `@RestControllerAdvice` is fragile; not blocking, but call out. Fix: in `design.md → §4` or `glossary.md → §5`, add a one-liner that the field switch is keyed off the DTO component name `limit` and protected by a unit test.
- **[D] Concurrent ingestion edge in tests vs requirements.** `requirements.md → US-3 AC #2` mentions "under append-only ingestion at newer timestamps." Good. But `design.md → §11 Test strategy` integration test "Concurrent insert during walk" only verifies *exclusion* of newer events; it doesn't verify behavior with a *same-timestamp* insert during a walk (UUIDv7-style id ordering matters here). Fix: either add a same-`recorded_at` test case or document that same-instant inserts are bounded by the `id DESC` tiebreaker and trust the keyset.
- **[C] `_delta.md` referenced indirectly.** A file `.specs/query-api/_delta.md` is untracked in git; out-of-scope trims reference scope changes but the delta log itself isn't linked from `requirements.md` or `design.md`. Not blocking, but if the delta is part of the spec history, link it. Fix: link `_delta.md` from `requirements.md → Out of scope` header, or move its content into the relevant doc and delete the file.
- **[E] Prerequisite slice ownership.** `design.md → §7 Prerequisites` correctly externalizes the RFC 7807 advice + correlation filter to a separate PR, but there's no link/anchor to where that slice is specified (a separate `.specs/` folder? an in-flight task?). Fix: link to the prerequisite spec/PR (or note "tracked in `tasks.md` T-1/T-2 of this feature") so reviewers can verify it actually exists.
