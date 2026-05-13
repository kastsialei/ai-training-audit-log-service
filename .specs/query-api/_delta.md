# Delta — 2026-05-13: Multi-actor query parameter

Product requirement added after the initial query API task set: `actor`
must accept a comma-separated list of up to 10 actor values in one request
(`?actor=a1,a2,...`). More than 10 actors is a client error.
Product clarification resolved the list semantics: SQL-equivalent
`actor IN (...)`, comma-separated single parameter only, no trimming around
commas, max 10 enforced before deduplication, and actor-list order
canonicalized for cursor fingerprints.

## requirements.md — US-1 and US-3 updated

- US-1 now distinguishes single-actor and comma-separated multi-actor calls.
- US-1 now requires exact matching against any supplied actor value while
  preserving AND semantics for the other filters.
- US-1 now requires `400 application/problem+json` when the actor list has
  more than 10 values before deduplication.
- US-1 now rejects repeated `actor` query parameters and whitespace-padded
  actor values.
- US-3 now requires cursor pagination to preserve the actor-list filter across
  pages without duplicates or skips.
- US-3 now treats actor-list order as equivalent for cursor filter matching.

## requirements.md — Open questions resolved

- Actor-list matching uses OR semantics.
- Repeated query parameters (`?actor=a1&actor=a2`) are rejected.
- Whitespace around commas is not trimmed; whitespace-padded values are invalid.
- Duplicate actor values count toward the 10-value limit before deduplication.
- Actor-list order is canonicalized for cursor fingerprints.

Why: design, glossary, tasks, and code still model `actor` as a single value.
The requirements change is recorded first so follow-up design/task/code updates
can trace to a clear product delta.

# Delta — 2026-05-11 (pass 2): InvalidAuditEventException → 400

Open question resolved during T-1 plan review: bad-input domain
validation on `POST /audit-events` must return `400`, not `500`. Threaded
top-down through the specs before updating the plan.

## requirements.md — US-4 added AC

- New AC under US-4: invalid `POST /audit-events` payloads that fail
  domain validation (today `500` via `InvalidAuditEventException`)
  return `400 application/problem+json` once the cross-cutting RFC 7807
  infra lands. Bad client input must never surface as `500`.

Why: shape-consistency alone is insufficient; the status code change
needs an explicit requirement so design/tasks/tests can trace to it.

## design.md — §7 Prerequisites and §10 Risk updated

- §7 Prerequisites #1 now lists `InvalidAuditEventException` among
  exceptions mapped by the global advice (to `400`), with a sentence
  citing the new US-4 AC.
- §10 Risk note for the prerequisite slice now explicitly calls out
  the `500 → 400` status change alongside the shape change.

Why: design must surface the contract change; otherwise T-1 review
risks rediscovering the question.

## tasks.md — T-1 Scope, DoD, Notes updated

- Scope: new bullet "Map `InvalidAuditEventException` to `400`".
- DoD: new failing-first integration coverage bullet for the `500 →
  400` migration.
- Notes: contract-change paragraph now lists shape change *and* status
  change.

Why: the plan can only cite tasks; tasks must own the new DoD bullet.

# Delta — 2026-05-11 (pass 1)

Scope-alignment pass. Trimmed design and tasks to match requirements;
moved trimmed concerns into requirements §Out of scope.

## requirements.md — added to §Out of scope

- Performance / latency SLOs (p95/p99/RPS, table-size guarantees)
- Observability beyond correlation ID (metrics, structured logs)
- API versioning strategy (`/v2/audit-events`)
- Cursor TTL / expiry
- 5xx response shape
- Unknown query parameter handling

Why: design and tasks carried these without a source in requirements.
Recording them as deliberate exclusions instead of dropping them silently.

## design.md — removed

- §1: "p99 latency bounded" → "off full scans"
- §2: "Unknown query parameters ignored" note
- §2: "Versioning" subsection
- §6: cursor "no expiry" bullet
- §6: "Downstream availability" (503) subsection
- §7: Observability subsection → one-line pointer to correlation-ID filter
- §8: latency/throughput/operability bullets → single deferred-to-SRE line

Why: each item was scope creep beyond requirements.

## tasks.md — removed / changed

- Removed T-12 "Add Query Observability" (orphan after design trim)
- Removed T-12 row from overview table and coverage table
- Removed `T-12` from Correlation-ID coverage row
- Rewrote T-9 Notes (drop "unknown params" contract)
- Coverage row consolidated to list all §Out of scope items honored
- Added 2026-05-11 revision note in Review section

Why: T-12 implemented design §7/§8 which were trimmed; remaining edits
remove dangling references.
