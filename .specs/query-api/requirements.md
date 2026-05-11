# Requirements: Query audit events (read endpoint)

## Problem

Audit events are durably ingested via `POST /audit-events`, but there is no
HTTP path to read them back. Compliance officers, SREs, and security analysts
— the consumers named in `product.md` — must query the database directly,
which:

- blocks compliance teams from self-service confirmation or refutation of an
  action during an audit;
- forces SREs investigating an incident to file ad-hoc DB requests instead of
  reconstructing the timeline of actions on a resource themselves;
- gives security analysts no stable, paginated retrieval primitive — direct
  SQL leaves no record of what was queried and risks duplicates or skipped
  rows when ingestion is happening concurrently.

The product promise of an auditable, traceable, append-only log cannot be
honored in practice without a query surface.

## User stories

### US-1: Compliance officer confirms an action

**As a** compliance officer
**I want** to filter audit events by actor, resource, event_type, outcome, and time range
**So that** I can confirm or refute that a specific action took place within a defined audit window

**Acceptance criteria**

- [ ] Given an `actor` and a time range, when I call `GET /audit-events?actor=u_42&from=2026-04-01T00:00:00Z&to=2026-05-01T00:00:00Z`, then the response is `200 application/json` with body `{ "items": [...], "next_cursor": "..."|null }`.
- [ ] Given multiple filters provided together, when I call the endpoint, then results match **all** filters (AND semantics) using **exact** match for `actor`, `resource`, `event_type`, and `outcome`.
- [ ] Given each `from` and `to` is independently optional, when I provide only `from`, then results are bounded below only; when I provide only `to`, only above; when I provide neither, time is unbounded (other filters still apply).
- [ ] Given the response, each `items[i]` exposes the same fields persisted by ingestion, in snake_case: `id` (UUID), `recorded_at` (UTC, ISO-8601 with offset), `actor` (string), `event_type` (string), `resource` (string), `outcome` (`SUCCESS`|`DENIED`|`ERROR`|null), `context` (JSON object).
- [ ] Given my window has zero matches, when I call the endpoint, then I receive `200` with `items: []` and `next_cursor: null`.

### US-2: SRE reconstructs a resource timeline

**As an** SRE
**I want** to filter audit events by `resource` and a time range
**So that** I can reconstruct the chronological sequence of actions on that resource during an incident

**Acceptance criteria**

- [ ] Given a `resource` and `from`/`to`, when I call the endpoint, then results are sorted by `(recorded_at DESC, id DESC)` (newest first) — stable under concurrent inserts at newer timestamps.
- [ ] Given the timestamps in `from`/`to`, then `from` is **inclusive** of its instant and `to` is **exclusive** (half-open interval), and both must be ISO-8601 with timezone information.
- [ ] Given `from > to`, when I call the endpoint, then the request is rejected with `400 application/problem+json` whose `detail` names the offending bounds.

### US-3: Security analyst paginates a large result set

**As a** security analyst
**I want** opaque cursor-based pagination
**So that** I can walk a large result set without loss or duplication, even while new events are being ingested

**Acceptance criteria**

- [ ] Given a result set larger than `limit`, when I call without `cursor`, then I receive the first page and a non-null `next_cursor`.
- [ ] Given a `next_cursor` from a prior response, when I pass it as `cursor=...`, then I receive the next contiguous page — no duplicates and no skipped events relative to the prior page (under append-only ingestion at newer timestamps).
- [ ] Given the last page, when I read it, then `next_cursor` is `null`.
- [ ] Given no `limit`, the default is `50`. Given `limit < 1` or `limit > 200`, then the request is rejected with `400 application/problem+json`.
- [ ] Given a malformed, tampered, or otherwise unparseable `cursor`, then the request is rejected with `400 application/problem+json` and the body does not leak cursor internals.
- [ ] Given a `cursor` issued for one filter set, when I replay it with a different filter set, then the request is rejected with `400 application/problem+json` whose `detail` says the cursor does not match the supplied filters.

### US-4: Caller gets actionable validation errors

**As a** caller of the read endpoint
**I want** clear, machine-readable validation errors and traceable responses
**So that** I can correct my request without guesswork and follow it through logs

**Acceptance criteria**

- [ ] Given **none** of the six substantive filters (`actor`, `resource`, `event_type`, `outcome`, `from`, `to`) is provided — `limit` and `cursor` do not count — then the request is rejected with `400 application/problem+json` and a `detail` stating at least one filter is required.
- [ ] Given any of: invalid ISO-8601 timestamp, unknown `outcome`, blank filter value, `from > to`, `limit` out of range, malformed `cursor` — when I call the endpoint, then the response is `400 application/problem+json` with a specific `detail` for the violation.
- [ ] Given any error response, then the body conforms to RFC 7807 (`type`, `title`, `status`, `detail`, `instance`) and is shape-consistent with ingestion error responses.
- [ ] Given any request with or without an `X-Correlation-Id` header, then the response includes the same correlation id (echoed if supplied; generated UUID otherwise) and the id is bound to MDC for the request lifetime, per ARCHITECTURE.md.

## Out of scope

- **AuthN / AuthZ.** Endpoint inherits ingestion's current unauthenticated posture. A separate ADR will introduce auth (likely tenant-scoped + role-based) before production rollout.
- **Full-text search, wildcards, prefix matching.** Exact match only — `product.md` non-goals already exclude search-engine semantics.
- **Aggregations and time-series rollups.** Out per product non-goals.
- **Total count of matched events.** Cursor pagination deliberately does not return a total; counting is a separate, expensive query and conflicts with append-only growth.
- **Alternative sort orders.** Single canonical sort `(recorded_at DESC, id DESC)`; no `sort=` parameter.
- **Schema changes.** No new columns, no actor/resource type split. Response mirrors today's flat schema.
- **Bulk export / streaming** (NDJSON, file download). Cursor pagination is the only retrieval mode.

## Open questions

- [ ] Which DB indexes back the filterable columns to avoid full-table scans? — owner: implementer; needed by: before merge. Candidates: `(recorded_at DESC, id DESC)` always; partial / composite on `(actor, recorded_at DESC)` and `(resource, recorded_at DESC)`. Resolved in `design.md`.
- [ ] Multi-value `outcome` filter (e.g., `outcome=DENIED,ERROR`) for security investigators? — owner: product; needed by: post-MVP. Default for v1: single value.
- [ ] Maximum time-window size and rate limiting to bound expensive scans? — owner: SRE; needed by: pre-prod hardening.
- [ ] Cursor signing / integrity (HMAC vs unsigned opaque blob)? — owner: implementer; needed by: design phase. Affects how "tampered cursor" is detected.
