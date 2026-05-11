# Tasks: Query audit events

> Filled in after `design.md` is locked. Tasks turn the design into a sequence
> of reviewable, mergeable units of work.

- Requirements: [./requirements.md](./requirements.md)
- Design: [./design.md](./design.md)
- Glossary (shared contracts, naming, ProblemKind enums): [./glossary.md](./glossary.md)

## Ground rules

These apply to **every** task in this file.

1. **One commit per task.** A task ships as a single commit (and a single PR
   if PRs are used). If you find yourself writing "and also..." -- split it.
2. **Size limit: 300-400 LOC of finished diff** (excluding generated files,
   lock files, fixtures, vendored code). If a task grows past this during
   implementation, **stop and split it** -- do not merge a larger commit.
3. **Every task links back.** Reference the requirements user story (`US-N`)
   and the design section it implements. No orphan tasks.
4. **Definition of Done is testable.** Each DoD bullet must be verifiable --
   a passing test, an observable behavior, a checked artifact. "Looks good"
   is not a DoD.
5. **Dependencies are explicit.** A task lists upstream tasks it needs and is
   not started until those are merged. If two tasks have no dependency,
   they can run in parallel.
6. **Test-first inside the task.** Per repo convention: failing test -> impl ->
   green. The DoD reflects that -- tests exist and pass.
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

| ID | Title | Depends on | Refs | Est. LOC | Plan | Status |
|---|---|---|---|---|---|---|
| T-1 | Add global RFC 7807 advice | -- | US-4, design 2/7 | ~300 | [T-1.md](plans/T-1.md) | todo |
| T-2 | Add correlation-ID filter | -- | US-4, design 2/7 | ~220 | [T-2.md](plans/T-2.md) | todo |
| T-3 | Promote shared audit event persistence types | -- | US-1, design 3 | ~180 | [T-3.md](plans/T-3.md) | todo |
| T-4 | Add query indexes migration | -- | US-1/US-2/US-3, design 3 | ~140 | [T-4.md](plans/T-4.md) | todo |
| T-5 | Model query filters and validation policy | T-3 | US-1/US-2/US-4, design 4/6 | ~260 | [T-5.md](plans/T-5.md) | todo |
| T-6 | Implement cursor codec and filter fingerprint | T-5 | US-3, design 5/6 | ~280 | [T-6.md](plans/T-6.md) | todo |
| T-7 | Implement query use case and reader port | T-5, T-6 | US-1/US-3/US-4, design 4/5 | ~330 | [T-7.md](plans/T-7.md) | todo |
| T-8 | Implement database query reader | T-3, T-4, T-7 | US-1/US-2, design 3/5 | ~360 | [T-8.md](plans/T-8.md) | todo |
| T-9 | Expose GET /audit-events success path | T-1, T-2, T-7, T-8 | US-1/US-2/US-3, design 2 | ~380 | [T-9.md](plans/T-9.md) | todo |
| T-10 | Complete query validation error contract | T-1, T-6, T-9 | US-3/US-4, design 4/6/7 | ~340 | [T-10.md](plans/T-10.md) | todo |
| T-11 | Harden pagination and concurrent-read tests | T-8, T-9, T-10 | US-2/US-3, design 5/11 | ~330 | [T-11.md](plans/T-11.md) | todo |

Status values: `todo`, `in-progress`, `in-review`, `done`, `blocked`.

Parallel starts: T-1, T-2, T-3, and T-4 can be developed independently.
Feature implementation starts at T-5 after the shared type promotion lands.

### Implementation progress checklist

Each task ticks off the same milestones in order. Use this to spot
half-finished work at a glance. Flip the matching `Status` column above
when a row reaches `Tests green`.

| ID | Plan written | Plan approved | Failing test | Implementation | Tests green | LOC ≤ 400 | Code review | Merged |
|---|---|---|---|---|---|---|---|---|
| T-1 | [x] | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |
| T-2 | [x] | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |
| T-3 | [x] | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |
| T-4 | [x] | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |
| T-5 | [x] | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |
| T-6 | [x] | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |
| T-7 | [x] | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |
| T-8 | [x] | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |
| T-9 | [x] | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |
| T-10 | [x] | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |
| T-11 | [x] | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] | [ ] |

---

## T-1: Add Global RFC 7807 Advice

**Refs:** US-4 (requirements.md), design.md section 2 "Error response", section 7 "Prerequisites"
**Depends on:** --
**Est. LOC:** ~300

### Goal

Land the cross-cutting error response prerequisite required before the query
endpoint is added. Existing ingestion errors should return the same RFC 7807
shape that the query endpoint will later use.

### Scope

- Add `api/shared/` `@RestControllerAdvice` that returns Spring
  `ProblemDetail` with `application/problem+json`.
- Map framework validation and binding failures named in design section 7:
  `MethodArgumentNotValidException`, `MethodArgumentTypeMismatchException`,
  `MissingServletRequestParameterException`, `HttpMessageNotReadableException`,
  plus a safe catch-all.
- Map the existing domain exception `InvalidAuditEventException` to
  `400 application/problem+json` (currently surfaces as `500`), per
  US-4 and design section 7.
- Preserve existing ingestion success behavior and update ingestion error
  assertions to the new problem shape.

### Out of scope

- Query-specific exceptions (`InvalidQueryException`, `InvalidCursorException`).
- Correlation-ID header/MDC behavior.
- New query endpoint code.

### Definition of Done

- [ ] Failing-first integration coverage proves invalid `POST /audit-events`
      returns `400 application/problem+json`.
- [ ] Failing-first integration coverage proves a `POST /audit-events`
      payload that triggers `InvalidAuditEventException` returns
      `400 application/problem+json` (was `500`).
- [ ] Error body contains RFC 7807 fields: `type`, `title`, `status`,
      `detail`, `instance`.
- [ ] Existing successful ingestion integration tests still pass unchanged in
      behavior.
- [ ] Catch-all mapping does not leak stack traces or exception internals in
      the response body.
- [ ] `./mvnw test` passes for unit tests touched by the task.
- [ ] Diff under 400 LOC (excluding generated).

### Notes / risks

This task intentionally changes the existing ingestion error contract before
the read endpoint exists, matching design section 7. That change is twofold:
(a) error body shape moves from Spring default to RFC 7807, and (b) bad-input
domain validation (`InvalidAuditEventException`) moves from `500` to `400`.
Keep problem `type` URIs stable under `https://audit-log-service/problems/`.

---

## T-2: Add Correlation-ID Filter

**Refs:** US-4 (requirements.md), design.md section 2 "Request headers", section 7 "Prerequisites"
**Depends on:** --
**Est. LOC:** ~220

### Goal

Install the cross-cutting request correlation prerequisite for both existing
ingestion requests and the future query endpoint.

### Scope

- Add an `api/shared/` `OncePerRequestFilter` that reads
  `X-Correlation-Id`, generates a UUID when absent, stores it in MDC under
  `correlationId`, and echoes it in the response header.
- Ensure MDC cleanup happens in `finally`.
- Add integration tests for supplied and generated correlation IDs on existing
  ingestion requests.

### Out of scope

- Request summary logging and query metrics.
- Query endpoint tests.
- RFC 7807 response body changes.

### Definition of Done

- [ ] Failing-first integration coverage proves a supplied
      `X-Correlation-Id` is echoed on `POST /audit-events`.
- [ ] Failing-first integration coverage proves a missing header produces a
      UUID-shaped response header.
- [ ] A 400 ingestion response also includes `X-Correlation-Id`.
- [ ] MDC is removed after request completion; this is covered by a focused
      filter unit test or a minimal integration assertion.
- [ ] `./mvnw test` passes for unit tests touched by the task.
- [ ] Diff under 400 LOC (excluding generated).

### Notes / risks

Use the MDC key already documented in `ARCHITECTURE.md`: `correlationId`.
Do not introduce a second header spelling.

---

## T-3: Promote Shared Audit Event Persistence Types

**Refs:** US-1 (requirements.md), design.md section 3 "Entity placement"
**Depends on:** --
**Est. LOC:** ~180

### Goal

Prepare the model for a second feature by moving audit event persistence
types from ingestion-specific packages to shared packages with no behavior
change.

### Scope

- Move `AuditEvent` and `Outcome` from `domain/ingestion/` to
  `domain/shared/`.
- Move `AuditEventRepository` from
  `infrastructure/persistence/ingestion/` to
  `infrastructure/persistence/shared/`.
- Update imports in ingestion code and tests.

### Out of scope

- New query domain models or use cases.
- Repository query methods for reading pages.
- Schema changes.

### Definition of Done

- [ ] Package moves are pure moves; `AuditEvent` and `Outcome` behavior is not
      changed.
- [ ] `AuditEventTest`, `IngestEventUseCaseTest`, and `IngestionIT` still
      pass after import updates.
- [ ] Architecture/layering tests still pass.
- [ ] No ingestion API response field changes.
- [ ] Diff under 400 LOC (excluding generated).

### Notes / risks

This task should be a clean rename/import commit. Avoid opportunistic cleanup
inside the moved classes.

---

## T-4: Add Query Indexes Migration

**Refs:** US-1, US-2, US-3 (requirements.md), design.md section 3 "New indexes"
**Depends on:** --
**Est. LOC:** ~140

### Goal

Add the database indexes that make the canonical query patterns viable before
the read adapter starts depending on them.

### Scope

- Add `V3__create_audit_events_query_indexes.sql`.
- Use `-- flyway:executeInTransaction=false` and
  `CREATE INDEX CONCURRENTLY`.
- Create the four indexes named in design section 3:
  `idx_audit_events_recorded_at_id`,
  `idx_audit_events_actor_recorded_at_id`,
  `idx_audit_events_resource_recorded_at_id`,
  `idx_audit_events_event_type_recorded_at_id`.
- Add a focused integration check that Flyway applies the migration and the
  expected indexes exist.

### Out of scope

- `outcome` indexes.
- Dropping or editing existing migrations.
- Query adapter implementation.

### Definition of Done

- [ ] Failing-first migration/integration test proves the four expected index
      names exist after Flyway runs.
- [ ] Migration uses concurrent index creation and opts out of Flyway's
      transaction.
- [ ] Existing migrations are not edited.
- [ ] `./mvnw verify` applies migrations cleanly in Testcontainers.
- [ ] Diff under 400 LOC (excluding generated).

### Notes / risks

Flyway transaction opt-out is the important gotcha. Never use a plain
transactional `CREATE INDEX` here.

---

## T-5: Model Query Filters and Validation Policy

**Refs:** US-1, US-2, US-4 (requirements.md), design.md section 4 "Validation rules", section 6 "Edge cases"
**Depends on:** T-3
**Est. LOC:** ~260

### Goal

Create the read-side application/domain model for query filters and its
non-HTTP validation rules.

### Scope

- Add `application/query/` value records for query criteria, requested limit,
  and time bounds.
- Enforce use-case-owned policy that at least one substantive filter is
  present; `limit` and `cursor` do not count.
- Enforce blank string rejection and `from > to` rejection while allowing
  `from == to`.
- Represent `outcome` with the shared `Outcome` enum.

### Out of scope

- Cursor encoding/decoding.
- HTTP parameter binding annotations.
- Database query implementation.

### Definition of Done

- [ ] Failing-first unit tests cover no-filter rejection, blank
      `actor`/`resource`/`event_type`, `from > to`, and `from == to`.
- [ ] Unit tests cover independently optional `from` and `to` bounds.
- [ ] Validation exceptions carry stable problem type identifiers or error
      codes that T-10 can map without string parsing.
- [ ] No Spring Web dependency is introduced into application/domain query
      models.
- [ ] Diff under 400 LOC (excluding generated).

### Notes / risks

Keep DTO shape out of this task. This task owns policy, not HTTP binding.

---

## T-6: Implement Cursor Codec and Filter Fingerprint

**Refs:** US-3 (requirements.md), design.md section 5 "Invariants", section 6 "Cursor format"
**Depends on:** T-5
**Est. LOC:** ~280

### Goal

Implement opaque cursor handling and filter-set binding without touching HTTP
or database code.

### Scope

- Add a cursor record containing `rt`, `id`, and `fp`.
- Encode/decode `base64url(JSON(...))` using UTC ISO-8601 timestamps.
- Compute the first 16 hex characters of
  `SHA-256(canonical(filters))`.
- Reject malformed cursors and filter mismatches via typed exceptions that do
  not expose cursor internals.

### Out of scope

- HMAC signing or cursor expiry.
- HTTP query parameter handling.
- Keyset SQL predicates.

### Definition of Done

- [ ] Failing-first unit tests cover encode/decode round trip.
- [ ] Unit tests cover canonical fingerprint stability for sorted keys,
      uppercased `outcome`, UTC `from`/`to`, and byte-exact string filters.
- [ ] Unit tests cover bad base64, bad JSON, missing fields, invalid UUID,
      invalid timestamp, and wrong fingerprint.
- [ ] Cursor error messages never include raw cursor contents.
- [ ] Diff under 400 LOC (excluding generated).

### Notes / risks

The cursor is opaque to callers but not secret. Do not add HMAC signing unless
the design is updated first.

---

## T-7: Implement Query Use Case and Reader Port

**Refs:** US-1, US-3, US-4 (requirements.md), design.md section 4 "Where rules live", section 5 "Invariants"
**Depends on:** T-5, T-6
**Est. LOC:** ~330

### Goal

Add the application use case that coordinates validation, cursor handling,
reader calls, and next-cursor generation.

### Scope

- Add `QueryAuditEventsUseCase` and an `EventReader` port owned by
  `application/query/`.
- Request `limit + 1` rows from the reader to determine whether
  `next_cursor` is needed.
- Decode incoming cursors, verify fingerprint against current filters, and
  pass keyset position to the reader.
- Return page items in reader order and encode `next_cursor` from the last
  returned item only when another page exists.

### Out of scope

- Persistence adapter implementation.
- HTTP controller/DTOs.
- Micrometer metrics or request logging.

### Definition of Done

- [ ] Failing-first unit tests with a fake `EventReader` cover criteria
      mapping and `limit + 1` pagination.
- [ ] Unit tests prove last page returns `next_cursor == null`.
- [ ] Unit tests prove a cursor with a different filter fingerprint is
      rejected before the reader is called.
- [ ] Unit tests prove no-filter policy from T-5 is enforced by the use case.
- [ ] Diff under 400 LOC (excluding generated).

### Notes / risks

Keep the reader port narrow: it should know criteria, keyset position, and row
limit, not HTTP concerns.

---

## T-8: Implement Database Query Reader

**Refs:** US-1, US-2 (requirements.md), design.md section 3 "Read patterns covered", section 5 "Sort stability"
**Depends on:** T-3, T-4, T-7
**Est. LOC:** ~360

### Goal

Implement the infrastructure adapter that reads immutable audit events from
Postgres using exact-match filters, half-open time bounds, canonical sorting,
and keyset pagination.

### Scope

- Add `infrastructure/persistence/query/` adapter implementing
  `EventReader`.
- Apply AND semantics for `actor`, `resource`, `event_type`, `outcome`,
  `from`, and `to`.
- Use `from` inclusive and `to` exclusive.
- Apply strict keyset predicate for `(recorded_at DESC, id DESC)` pagination.
- Sort by `(recorded_at DESC, id DESC)`.

### Out of scope

- HTTP endpoint wiring.
- Query-specific RFC 7807 mappings.
- Metrics/logging.

### Definition of Done

- [ ] Failing-first integration tests seed audit events and prove exact-match
      AND filters.
- [ ] Integration tests prove newest-first ordering and deterministic UUID
      tie-break for equal `recorded_at`.
- [ ] Integration tests prove `from` inclusive, `to` exclusive, and
      independently optional time bounds.
- [ ] Integration tests prove keyset predicate returns the next contiguous DB
      slice after a supplied cursor position.
- [ ] `EXPLAIN (FORMAT JSON)` test proves an `actor + from/to` query can use
      `idx_audit_events_actor_recorded_at_id`.
- [ ] Diff under 400 LOC (excluding generated).

### Notes / risks

Prefer structured JPA/SQL APIs over ad hoc string concatenation. If dynamic SQL
becomes too large for this task, split adapter tests from implementation
before merging.

---

## T-9: Expose GET /audit-events Success Path

**Refs:** US-1, US-2, US-3 (requirements.md), design.md section 2 "Public surface / contract"
**Depends on:** T-1, T-2, T-7, T-8
**Est. LOC:** ~380

### Goal

Expose the read endpoint for successful queries and map use-case results into
the public snake_case response envelope.

### Scope

- Add `api/query/` controller and DTO records for `GET /audit-events`.
- Bind query parameters: `actor`, `resource`, `event_type`, `outcome`,
  `from`, `to`, `limit`, and `cursor`.
- Default `limit` to 50 and cap successful response page size to the
  requested limit.
- Serialize response as `{ "items": [...], "next_cursor": "..."|null }`.
- Map item fields exactly as persisted: `id`, `recorded_at`, `actor`,
  `event_type`, `resource`, `outcome`, `context`.

### Out of scope

- Full 400 error matrix; T-10 owns validation/error completion.
- Pagination invariant/property tests; T-11 owns hardening.
- Observability.

### Definition of Done

- [ ] Failing-first MockMvc/Testcontainers test proves a filtered
      `GET /audit-events` returns `200 application/json`.
- [ ] Integration tests assert snake_case envelope and item fields.
- [ ] Integration tests cover empty result set with `items: []` and
      `next_cursor: null`.
- [ ] Integration tests cover multiple filters with AND semantics through the
      HTTP endpoint.
- [ ] Integration test proves response order is `(recorded_at DESC, id DESC)`.
- [ ] Integration test proves first page over `limit` emits a non-null
      `next_cursor`.
- [ ] Supplied or generated `X-Correlation-Id` is present on successful query
      responses.
- [ ] Diff under 400 LOC (excluding generated).

### Notes / risks

Do not add a `sort` or total-count field. Unknown-parameter handling is
explicitly out of scope (`requirements.md` §Out of scope) — accept the
framework default without specifying it as a contract.

---

## T-10: Complete Query Validation Error Contract

**Refs:** US-3, US-4 (requirements.md), design.md section 4 "Validation rules", section 6 "Edge cases", section 7 "Integration points"
**Depends on:** T-1, T-6, T-9
**Est. LOC:** ~340

### Goal

Finish all query-specific 400 responses with stable RFC 7807 problem types and
safe details.

### Scope

- Extend global advice with query-owned `InvalidQueryException` and
  `InvalidCursorException` mappings.
- Ensure query parameter binding failures map to the design section 4 problem types:
  `no-filter`, `blank-filter`, `invalid-outcome`, `invalid-timestamp`,
  `invalid-time-range`, `invalid-limit`, `invalid-cursor`,
  `cursor-filter-mismatch`.
- Validate `limit` range `1 <= limit <= 200`.
- Ensure timestamp inputs require timezone information.
- Ensure malformed/tampered cursor responses do not leak cursor internals.

### Out of scope

- Success response behavior except where needed to trigger validation.
- HMAC cursor signing.
- AuthN/AuthZ.

### Definition of Done

- [ ] Failing-first integration tests cover no substantive filters, only
      `limit`, only `cursor`, blank string filters, and whitespace-only
      filters.
- [ ] Integration tests cover invalid `outcome`, invalid timestamp,
      timestamp without offset, `from > to`, `limit < 1`, and `limit > 200`.
- [ ] Integration tests cover malformed cursor and cursor/filter mismatch.
- [ ] Every 400 response has `application/problem+json`, RFC 7807 fields, and
      `X-Correlation-Id`.
- [ ] Cursor error `detail` values do not echo the supplied cursor or decoded
      JSON fields.
- [ ] Diff under 400 LOC (excluding generated).

### Notes / risks

Keep validation ownership aligned with design section 4: DTO/binder for shape,
use case for policy, cursor codec/use case for cursor validity.

---

## T-11: Harden Pagination and Concurrent-Read Tests

**Refs:** US-2, US-3 (requirements.md), design.md section 5 "Invariants", section 11 "Test strategy"
**Depends on:** T-8, T-9, T-10
**Est. LOC:** ~330

### Goal

Prove the most failure-prone read invariants at the HTTP/integration level
after the endpoint contract is complete.

### Scope

- Add a multi-page HTTP pagination walk test with `limit=2` over at least
  three pages.
- Add a concurrent insert during walk test: page 1, insert newer event, page 2.
- Add randomized/property-style pagination invariant coverage comparing a
  cursor walk with the same filtered result set read as one ordered list.
- Cover `from == to` as an empty half-open window.

### Out of scope

- New production code unless a test exposes a defect.
- Alternative sort orders or total counts.
- Performance benchmarking.

### Definition of Done

- [ ] Pagination walk test proves no duplicates, no skips, and
      `next_cursor: null` on the last page.
- [ ] Concurrent insert test proves a newer event does not appear in the
      in-progress cursor walk.
- [ ] Randomized invariant test samples at least 100 scenarios and compares
      walked IDs to the single-query ordered IDs.
- [ ] `from == to` returns `200` with empty `items` and `next_cursor: null`.
- [ ] `./mvnw verify` passes.
- [ ] Diff under 400 LOC (excluding generated).

### Notes / risks

If randomized coverage is flaky because of clock precision or fixture setup,
fix the deterministic fixture control rather than weakening the invariant.

---

## Coverage Review

| Requirement / design concern | Covered by |
|---|---|
| `GET /audit-events` public contract and response envelope | T-9 |
| Exact-match filters with AND semantics | T-5, T-8, T-9 |
| Optional `from`/`to`, inclusive lower bound, exclusive upper bound | T-5, T-8, T-11 |
| Sort `(recorded_at DESC, id DESC)` | T-8, T-9 |
| Cursor pagination, filter binding, malformed cursor handling | T-6, T-7, T-9, T-10, T-11 |
| Limit default and range | T-7, T-9, T-10 |
| RFC 7807 error shape | T-1, T-10 |
| Correlation ID response header and MDC | T-2, T-9, T-10 |
| Shared entity/repository promotion | T-3 |
| Query indexes and index usage | T-4, T-8 |
| Out-of-scope exclusions: auth, search, totals, alternate sorts, export, NFR SLOs, metrics/logging, versioning, cursor TTL, 5xx shape, unknown-param contract | Honored by omission across tasks |

## Review

Filled in by the tech lead / architect before implementation starts.

- [x] Each task references a user story and a design section
- [x] Each task fits in one commit <= 400 LOC; oversized tasks were split
- [x] DoD bullets are testable / observable, not subjective
- [x] Dependencies form a DAG (no cycles), parallelizable tasks identified
- [x] No task contains work outside `design.md`; new scope was added to design first
- [x] Sequencing minimizes long-lived branches and merge conflicts

**Reviewer:** Codex (acting tech lead review pass)
**Reviewed on:** 2026-05-06
**Verdict:** approved
**Notes:** The first draft risked bundling cross-cutting prerequisites with
query API work and bundling cursor logic into the use case. The final split
separates RFC 7807, correlation ID, shared model promotion, cursor codec,
query policy, database reader, HTTP contract, validation matrix, and
pagination hardening into independently reviewable commits.

**Revision (2026-05-11):** Observability task (T-12) removed after design
trim aligned scope with `requirements.md` — metrics, INFO logging schema,
and NFR SLOs were moved to §Out of scope. Correlation-ID coverage stays
in T-2, T-9, T-10.

**Revision (2026-05-11, planning pass):** Per-task implementation plans
written under `plans/T-1.md` … `plans/T-11.md`. Each plan covers Goal,
Approach, Files, test-first Step-by-step, DoD trace, Risks, Review, and
Integration & handoff sections. Team-lead consolidated review verdict:
approve T-3, T-4, T-11 as written; approve T-5, T-6, T-7, T-8, T-9, T-10
with a small reconciliation pass before implementation (see
`plans/` review notes — exception-API contract lock, `QueryCriteria`
vs `RequestedLimit` ownership, T-9 request-object vs T-10 DTO validators).
Progress checklist table above tracks each task through plan-approval
to merged.
