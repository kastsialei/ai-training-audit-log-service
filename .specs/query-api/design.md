# Design: Query audit events (read endpoint)

- Requirements: [./requirements.md](./requirements.md)
- Related ADRs / prior designs: ADR-0002 (layering), ADR-0003 (feature
  packaging), ADR-0004 (JPA in domain), ADR-0007 (append-only triggers).
  No prior query design.

---

## 1. Framing

**Problem.** Audit events can be written but not read over HTTP; consumers
named in `product.md` are forced to query the DB directly, defeating the
auditability promise.

**Smallest change that solves it.** A single `GET /audit-events`
endpoint with:

- a fixed set of equality filters + half-open time range,
- canonical sort `(recorded_at DESC, id DESC)`,
- opaque cursor pagination (no offsets, no totals),
- RFC 7807 errors for everything 4xx,
- composite indexes on the filter columns to keep p99 latency bounded.

**Deliberately not building** (verbatim from `requirements.md` *Out of
scope*): authn/authz, full-text search, aggregations, total counts,
alternative sort orders, schema changes, bulk export. Plus: no caching
layer, no async query, no streaming response.

**Constraints not in requirements.**

- The codebase has *no* `@RestControllerAdvice`, *no* RFC 7807 error
  shape, *no* correlation-ID filter today, although `ARCHITECTURE.md`
  mandates all three. They are cross-cutting and **land in a separate
  prerequisite slice before this one** (see §7 "Integration points →
  Prerequisites"). This design assumes both are present at
  implementation start.
- The existing `AuditEvent` entity lives in `domain/ingestion/`. Per
  ADR-0003, types depended on by 2+ features are promoted to
  `domain/shared/` only when that happens. This is now that moment —
  see §3 "Entity placement". The promotion is part of *this* slice.

**Consumers.** Internal services + humans (compliance officers,
SREs, security analysts). No external/public exposure in v1.

## 2. Public surface / contract

### Endpoint

```
GET /audit-events
```

Synchronous, JSON, currently unauthenticated (inherits ingestion's
posture; auth is a separate ADR per `requirements.md` §Out of scope).

### Query parameters

| Name | Type | Required | Default | Notes |
|---|---|---|---|---|
| `actor` | string | no¹ | — | exact match, case-sensitive |
| `resource` | string | no¹ | — | exact match, case-sensitive |
| `event_type` | string | no¹ | — | exact match, case-sensitive |
| `outcome` | enum: `SUCCESS`\|`DENIED`\|`ERROR` | no¹ | — | exact, uppercase |
| `from` | ISO-8601 instant w/ offset | no¹ | unbounded | inclusive |
| `to` | ISO-8601 instant w/ offset | no¹ | unbounded | exclusive |
| `limit` | integer | no | `50` | `1 ≤ limit ≤ 200` |
| `cursor` | opaque base64url string | no | — | issued by prior response |

¹ At least **one** of the six substantive filters
(`actor`, `resource`, `event_type`, `outcome`, `from`, `to`) must be
provided. `limit` and `cursor` do not satisfy this rule.

Unknown query parameters are **ignored** (Spring's default; documented
explicitly so future filters don't silently break clients).

### Request headers

| Header | Required | Notes |
|---|---|---|
| `X-Correlation-Id` | no | echoed in response if supplied; UUID generated otherwise |

### Success response

`200 OK`, `Content-Type: application/json`.

```json
{
  "items": [
    {
      "id": "1f0a8...uuid",
      "recorded_at": "2026-04-30T14:22:01.123Z",
      "actor": "u_42",
      "event_type": "doc.read",
      "resource": "doc:9821",
      "outcome": "SUCCESS",
      "context": { "ip": "10.0.0.1" }
    }
  ],
  "next_cursor": "eyJydCI6Ii4uLiIsImlkIjoiLi4uIiwiZnAiOiIuLi4ifQ"
}
```

`next_cursor` is `null` on the last page (also when `items` is empty).

`outcome` may be `null` — mirrors persisted nullability.

### Error response

All 4xx and 5xx use RFC 7807. Shape (Spring `ProblemDetail`):

```
HTTP/1.1 400 Bad Request
Content-Type: application/problem+json
X-Correlation-Id: <id>
```

```json
{
  "type": "https://audit-log-service/problems/invalid-time-range",
  "title": "Invalid request",
  "status": 400,
  "detail": "from must be earlier than to (got from=2026-05-01T..., to=2026-04-01T...)",
  "instance": "/audit-events"
}
```

`type` URIs are stable and feature-namespaced; full list in §4.

### Versioning

New endpoint — no compatibility constraint. Future schema additions to
items are non-breaking only if optional. Breaking changes will be done
under a new path (`/v2/audit-events`), not via a query parameter.

### Idempotency

GET is naturally idempotent. Cursor pagination is **page-stable** under
append-only ingestion at *newer* timestamps (see §5 invariants).

## 3. Data

**No new tables. No new columns.** Read schema is exactly the existing
`audit_events` table (V2 migration).

### Entity placement

The existing `AuditEvent` entity in `domain/ingestion/` is now read by a
second feature. Per ADR-0003, **promote it to `domain/shared/`** as part
of this slice. Same for `Outcome`. This is a pure move: no logic
changes; package name updates only. `AuditEventRepository` (Spring Data)
similarly moves to `infrastructure/persistence/shared/` (it is a thin
CRUD layer with no feature-specific behavior).

Rejected: cross-feature imports from `application/query/` into
`domain/ingestion/`. Plausible short-term, but ADR-0003's promotion
rule was written precisely for this case — applying it keeps the
package graph honest.

### New indexes (V3 migration)

A migration `V3__create_audit_events_query_indexes.sql` adds:

```sql
CREATE INDEX idx_audit_events_recorded_at_id
    ON audit_events (recorded_at DESC, id DESC);

CREATE INDEX idx_audit_events_actor_recorded_at_id
    ON audit_events (actor, recorded_at DESC, id DESC);

CREATE INDEX idx_audit_events_resource_recorded_at_id
    ON audit_events (resource, recorded_at DESC, id DESC);

CREATE INDEX idx_audit_events_event_type_recorded_at_id
    ON audit_events (event_type, recorded_at DESC, id DESC);
```

Rationale per index:

- `(recorded_at DESC, id DESC)` — baseline for time-only queries and
  for the keyset `WHERE (recorded_at, id) < (?, ?)` predicate.
- The three `(filter, recorded_at DESC, id DESC)` composites cover the
  common single-equality + time-range pattern with no extra sort.
- **`outcome` deliberately omitted** — three-value enum is too low
  cardinality for a useful index alone; queries with `outcome` plus
  another filter use the other filter's index and a recheck.

Estimated runtime: indexes are non-blocking with `CREATE INDEX
CONCURRENTLY`. **However Flyway runs migrations inside a transaction by
default**, so V3 must opt out (`-- flyway:executeInTransaction=false`)
and use `CONCURRENTLY`. On a ~10M-row table this is ~1–3 minutes per
index.

Storage growth: each index is ~5–10% of table size depending on column
selectivity. Acceptable.

### Read patterns covered

| Filter combination | Index used |
|---|---|
| `from`/`to` only | `idx_audit_events_recorded_at_id` |
| `actor` (+time) | `idx_audit_events_actor_recorded_at_id` |
| `resource` (+time) | `idx_audit_events_resource_recorded_at_id` |
| `event_type` (+time) | `idx_audit_events_event_type_recorded_at_id` |
| `actor` + `resource` (+time) | planner picks the more selective; recheck other |
| `outcome` only (+time) | baseline + recheck on `outcome` |

### Retention / archival

Out of scope for v1 — table grows append-only; no read-side action.
Listed in §12 open questions.

## 4. Validation rules

All validation failures return `400 application/problem+json`. Each
violation maps to a stable `type` URI under
`https://audit-log-service/problems/`:

| Rule | Where enforced | Error `type` | `detail` example |
|---|---|---|---|
| ≥1 substantive filter | use case (post-binding) | `…/no-filter` | "at least one of actor, resource, event_type, outcome, from, to is required" |
| `actor`/`resource`/`event_type` non-blank if present | request DTO `@AssertTrue` | `…/blank-filter` | "actor must not be blank" |
| `outcome ∈ {SUCCESS,DENIED,ERROR}` | Spring binder → `MethodArgumentTypeMismatchException` | `…/invalid-outcome` | "outcome must be one of SUCCESS, DENIED, ERROR" |
| `from`, `to` parseable as ISO-8601 with offset | Spring binder | `…/invalid-timestamp` | "to is not a valid ISO-8601 timestamp with offset" |
| `from < to` (both present) | request DTO `@AssertTrue` | `…/invalid-time-range` | "from must be earlier than to" |
| `1 ≤ limit ≤ 200` | request DTO `@Min`/`@Max` | `…/invalid-limit` | "limit must be between 1 and 200" |
| `cursor` parseable + structurally valid | use case | `…/invalid-cursor` | "cursor is malformed" — no internals |
| `cursor.fingerprint` matches current filter set | use case | `…/cursor-filter-mismatch` | "cursor was issued for a different filter set" |

**Normalization.** None — filters are matched byte-exact. `from`/`to`
are converted to UTC `OffsetDateTime` after parsing (offsets honored on
input, persisted comparisons in UTC).

**Layered defense.** DB-level CHECK constraints (V2) defend against
blank values from the write side and remain authoritative; the read
side does not need DB-level read guards.

**Where rules live.**

- Per-field shape (parsing, blank, range): request DTO + Spring binder.
- Cross-field (`from < to`): DTO `@AssertTrue` is acceptable.
- `≥1 substantive filter`: lives in the use case, **not** the DTO,
  because `limit` and `cursor` must explicitly not count — keeping
  this in the use case preserves DTO-as-shape and use-case-as-policy.
- Cursor: use case (it owns the cursor codec).

## 5. Invariants

The contract callers can rely on:

1. **Sort stability.** Result sequence is total-ordered by
   `(recorded_at DESC, id DESC)`. Two events with equal `recorded_at`
   are tie-broken deterministically by `id DESC`. Enforced by SQL
   `ORDER BY` in the query repository.

2. **Page-stability under newer-timestamp ingestion.** Walking
   `cursor`-by-`cursor` from a first page never duplicates an event
   and never skips one *that existed at or before the cursor's
   `recorded_at`*. Enforced by the strict-less-than keyset predicate
   `(recorded_at, id) < (cursor.rt, cursor.id)`. Reason this works:
   ingestion only writes events with `recorded_at = now()`, so newly
   inserted rows are always greater than any cursor — they cannot
   appear on later pages of an in-progress walk; they only appear on
   subsequent fresh queries.

3. **Cursor binds to its filter set.** A `cursor` issued for filter
   set F is rejected if replayed against filter set F'≠F.
   Enforced by an embedded fingerprint over canonicalized filters
   (see §6 "Cursor format").

4. **Read returns only persisted, immutable events.** ADR-0007
   triggers prevent UPDATE/DELETE; the read path therefore never sees
   a partially-mutated event.

5. **Time monotonicity in stored events.** Inherited from ingestion —
   server-stamped `now()` is monotonic non-decreasing under a single
   Postgres instance. Read code does not re-validate.

If any invariant is violated, downstream impact is severe:
duplicate/skipped events break compliance reconstructions;
fingerprint bypass breaks cursor's filter-binding guarantee.

## 6. Edge cases & failure modes

### Empty / null / missing input

- All filters absent → 400 (no-filter rule).
- Only `limit`/`cursor` provided → 400 (no-filter rule).
- Empty string `actor=` → 400 (blank-filter rule).
- `outcome=` (empty) → 400 (Spring binder rejects empty enum).
- `from=`/`to=` (empty) → 400 (binder).
- Whitespace-only filter (`actor=%20`) → 400 (blank after trim — note:
  trim applies *only* to the blank check; matching itself is byte-exact).

### `from`/`to` corner cases

- `from == to` → permitted; window is half-open, so result is empty.
- `from > to` → 400 (`invalid-time-range`).
- Either bound missing → window unbounded on that side.
- `from` or `to` without offset (`2026-05-01T00:00:00`) → 400 (offset
  is mandatory to avoid silent timezone bugs).

### Concurrent ingestion

- Newer events than any cursor's `recorded_at` are excluded from the
  in-progress walk by design (see invariant 2). They appear on a
  fresh query. Documented; not a bug.

### Cursor failure modes

- Malformed (not base64url, bad JSON) → 400 `invalid-cursor`.
  Body never echoes cursor contents — leak avoidance.
- Tampered (valid base64 + JSON, but fingerprint missing or wrong) →
  400 `cursor-filter-mismatch` if mismatched against current filter
  set; 400 `invalid-cursor` if structurally invalid.
- Cursor pointing to an `id`/`recorded_at` that no longer exists —
  not possible by ADR-0007 (no deletes). If somehow stale, the keyset
  predicate `(recorded_at, id) <` still works correctly.
- Cursor used after a long delay → no expiry. Acceptable for v1; a
  future change can add issued-at timestamp.

### Cursor format (concrete)

Encoded: `base64url(JSON({"rt": "<ISO-8601 UTC>", "id": "<uuid>", "fp": "<16 hex>"}))`.

- `rt` — `recorded_at` of the **last item of the page just returned**, in
  UTC ISO-8601 with offset.
- `id` — its `id`.
- `fp` — first 16 hex chars of `SHA-256(canonical(filters))`.

`canonical(filters)` = JSON object with sorted keys containing only the
non-null substantive filters in their normalized form (uppercased
`outcome`, UTC-converted `from`/`to`, byte-exact strings for
`actor`/`resource`/`event_type`).

**Not signed (no HMAC).** Cursors do not grant access to anything not
already permitted by the filter set; tampering can only damage the
caller's own walk. The fingerprint is for *correctness* (detect filter
swap), not security. §12 carries the security-review handoff for
upgrading to HMAC if required pre-prod.

### Limit at edges

- `limit=1` → page of 1 + cursor (unless last).
- `limit=200` → maximum.
- `limit=201` → 400.
- `limit=0` / negative → 400.
- Result set < limit → no `next_cursor`.

### Oversize / pathological responses

- `context` is unbounded JSONB on disk. v1 returns it as-is. If a
  single response exceeds Spring's default JSON serialization buffer
  it'll fail with 500 `internal-error`. Acceptable for v1; tracked
  in §12 for a max-size guard.

### Time / clock concerns

- Server reads UTC `TIMESTAMPTZ`; Java side is `OffsetDateTime`.
- `from`/`to` may carry any offset; comparison normalizes to UTC.
- No client-clock dependence on the read path.

### Downstream availability

- Postgres unavailable → request fails-closed with `503` (RFC 7807).
- No retries on the read path — caller retries.

## 7. Integration points

### Prerequisites (separate slice — must land before this one)

Two pieces of cross-cutting infra are mandated by `ARCHITECTURE.md`
but absent today. They are scoped to a **separate, smaller PR** that
lands ahead of this slice and is reviewed in isolation (so that the
ingestion error-shape change is decoupled from the new feature). Both
live in `api/shared/`:

1. **Global RFC 7807 advice.** `@RestControllerAdvice` mapping
   `MethodArgumentNotValidException`,
   `MethodArgumentTypeMismatchException`,
   `MissingServletRequestParameterException`,
   `HttpMessageNotReadableException`, plus a catch-all to RFC 7807
   `ProblemDetail`. Replaces Spring's default error body for *every*
   endpoint, including the existing POST `/audit-events`. The
   prerequisite slice updates `IngestionIT` accordingly.

2. **Correlation-ID filter.** `OncePerRequestFilter` reading
   `X-Correlation-Id` (generates UUID if missing), setting
   `MDC.put("correlationId", …)` and adding `X-Correlation-Id` to the
   response. `MDC.remove` in `finally`. Active for both ingest and
   query.

**This slice extends** the advice with two query-specific exception
types it owns: `InvalidQueryException` and `InvalidCursorException`,
each mapped to a stable `type` URI per §4. The filter is consumed
as-is.

### Reads from / writes to

- Reads from `audit_events` (Postgres) only. No writes.
- No outbound network calls.

### Observability

- Per-request log line at `INFO` with: method, path, status, latency
  ms, `correlationId`, filter-fingerprint, `limit`, `pageSize`, and
  whether `cursor` was supplied. **Filter values are not logged at
  `INFO`** to avoid PII leakage (e.g., `actor` may be a user id);
  only the fingerprint and which filters were present.
- At `DEBUG` (off in prod), filter values may be logged.
- Metric: `audit_events_query_seconds` histogram tagged by
  `has_actor`, `has_resource`, `has_event_type`, `has_outcome`,
  `has_time_window`, `result_size_bucket`. Built on Micrometer (added
  by this slice — no metric infra exists today).

## 8. Non-functional requirements

- **Latency target.** p95 < 200 ms for queries hitting an index on
  tables up to 50M rows. p99 < 500 ms. (No SLO doc exists; targets
  are starting points to be confirmed in §12.)
- **Throughput.** Read endpoint expected to handle ≤50 RPS in v1.
- **Security.**
  - No authn/authz in v1 (inherits ingestion).
  - Filter values may contain PII; logged only at `DEBUG`.
  - Cursor not signed; threat model: caller tampering with their own
    cursor harms only themselves (see §6 "Cursor failure modes").
  - Error bodies must not echo internal cursor structure or stack
    traces.
- **Compliance.** None additional in v1. Auth ADR is the gate to
  production rollout (`requirements.md` §Out of scope).
- **Operability.** Single Postgres dependency; no new alerts beyond
  the existing DB liveness check. Runbook update lives with the PR.

## 9. Alternatives considered

- **Offset/limit pagination** — rejected. Append-only growth makes
  offsets drift; consumers cannot reliably resume; deep pages
  degrade.
- **HMAC-signed cursors** — rejected for v1. The fingerprint check
  catches the only correctness-relevant attack (filter swap); cursor
  contents leak only `(recorded_at, id)` of an event the caller
  could already retrieve. Carried as §12 open question for security
  review.
- **GraphQL / generic search** — rejected per `product.md` non-goals.
- **Partial index on `outcome` per value** (e.g.,
  `WHERE outcome='DENIED'`) — rejected for v1; selectivity not yet
  observed in production. Revisit after baseline metrics.
- **Pushing the "≥1 filter" rule into the DTO** — rejected because
  `limit` and `cursor` don't count; keeping the rule in the use case
  preserves DTO-as-shape and use-case-as-policy.
- **Cross-feature import (skip the `shared/` promotion)** — rejected.
  ADR-0003's promotion rule fires now that two features depend on
  `AuditEvent`; bypassing it leaves a quiet violation.

## 10. Risk & rollout

**Blast radius.**
- Incorrect cursor logic could silently dedupe or skip events,
  invalidating compliance reconstructions. Mitigated by §11 keyset /
  property tests.
- Missing index → full-table scan under load → DB CPU spike.
  Mitigated by the V3 migration shipping in the same PR; the
  integration test in §11 confirms index usage (`EXPLAIN`).
- Ingestion's 400 response shape changes (Spring default → RFC 7807)
  in the *prerequisite* slice, not this one. By the time this slice
  lands, RFC 7807 is already the contract for every endpoint, so this
  PR introduces no surprise to existing clients.

**Rollout.**
- Single environment promotion. No feature flag — endpoint is
  additive; rollback = revert PR + revert V3 migration (indexes can
  be dropped without data loss).
- V3 migration uses `CREATE INDEX CONCURRENTLY` so it doesn't block
  ingestion.

**Rollback.**
- Code revert is clean (no schema change beyond additive indexes).
- Indexes can be dropped on rollback if they cause unexpected write
  amplification: `DROP INDEX CONCURRENTLY …` in a follow-up
  migration. (Never edit V3.)

## 11. Test strategy

Each acceptance criterion in `requirements.md` maps to ≥1 test below.

### Unit (`*Test.java`, no Spring, no DB)

- `EventFilter` validation: ≥1 substantive filter, blank rejection,
  `from < to`.
- `Cursor` codec: encode/decode round-trip, fingerprint computation,
  malformed-input rejection (each: bad base64, bad JSON, missing
  fields, wrong fingerprint), no-leak guarantee on error messages.
- `QueryEventsUseCase` with mocked `EventReader`: filter→criteria
  mapping, cursor decode + filter-mismatch rejection, page-size +1
  trick to compute `next_cursor`.

### Integration (`*IT.java`, Testcontainers Postgres + Flyway + MockMvc)

- **Happy path per user story** (US-1, US-2, US-3): seed events,
  call endpoint, assert envelope shape, item field set, snake_case
  serialization, sort order.
- **Pagination walk**: ≥3 pages with `limit=2`; assert no
  duplicates, no skips, last page has `next_cursor: null`.
- **Concurrent insert during walk**: page 1 → insert newer event →
  page 2; new event must not appear on the walk.
- **Filter combinations**: each pair of substantive filters → assert
  AND semantics.
- **`from == to`** → empty `items`, `next_cursor: null`.
- **All 400 cases from §4 table**: one test per error `type`,
  asserting status, `Content-Type: application/problem+json`,
  presence of `type`/`title`/`status`/`detail`/`instance`, no leak
  of cursor internals on cursor errors.
- **Correlation ID**: header echoed when supplied; UUID generated
  when absent; present on both 200 and 400.
- **Index usage** (one test): `EXPLAIN (FORMAT JSON)` on a
  representative `actor + from/to` query asserts an `Index Scan` on
  `idx_audit_events_actor_recorded_at_id`.

### Property / invariant tests

- Pagination invariant: random filter set + random page size →
  walking all pages yields exactly the same multi-set as a single
  un-paginated SQL query against the same filters. Sample 100
  random scenarios.

### Manual / exploratory

- Smoke test against `docker-compose up`: insert via POST, query via
  GET with each filter combination, observe correlation ID in
  response and logs.

## 12. Open questions

- [ ] HMAC-sign cursors? — owner: security review; blocks: pre-prod
  rollout. Default for v1: unsigned + fingerprint.
- [ ] Multi-value `outcome` filter (`outcome=DENIED,ERROR`) for
  security investigators — owner: product; blocks: nothing in v1.
- [ ] Maximum time-window size and rate limiting — owner: SRE;
  blocks: pre-prod hardening. Likely a follow-up middleware.
- [ ] Per-response payload size cap (oversized `context`) — owner:
  implementer; blocks: nothing in v1; revisit if 5xx observed.
- [ ] Confirm latency SLOs (p95/p99) — owner: SRE; blocks: pre-prod.
- [ ] Retention / archival policy — owner: product + DBA; blocks:
  long-term storage growth, not v1.
