# Query API: shared contracts and naming

Single source of truth for cross-task names, types, packages, and
exception structure. All plans under `plans/` follow this glossary. If
a plan disagrees, **this glossary wins** and the plan must be updated.

- Requirements: [./requirements.md](./requirements.md)
- Design: [./design.md](./design.md)
- Tasks: [./tasks.md](./tasks.md)

---

## 1. Packages

| Concern | Package |
|---|---|
| Domain types shared by 2+ features | `net.sam.ai.engineering.audit.domain.shared` |
| Domain types owned by ingestion only | `net.sam.ai.engineering.audit.domain.ingestion` |
| Application (use cases, ports) for query | `net.sam.ai.engineering.audit.application.query` |
| Application for ingestion | `net.sam.ai.engineering.audit.application.ingestion` |
| Persistence adapter shared by 2+ features | `net.sam.ai.engineering.audit.infrastructure.persistence.shared` |
| Persistence adapter for query | `net.sam.ai.engineering.audit.infrastructure.persistence.query` |
| API cross-cutting (filters, advice, problem types) | `net.sam.ai.engineering.audit.api.shared` |
| API for query | `net.sam.ai.engineering.audit.api.query` |

## 2. Records and value types

| Type | Package | Fields | Owner task |
|---|---|---|---|
| `AuditEvent` (entity) | `domain.shared` | as today | T-3 (promote) |
| `Outcome` (enum) | `domain.shared` | `SUCCESS`, `DENIED`, `ERROR` | T-3 (promote) |
| `QueryCriteria` (record) | `application.query` | `String actor, String resource, String eventType, Outcome outcome, TimeRange timeRange` | T-5 |
| `TimeRange` (record) | `application.query` | `OffsetDateTime from, OffsetDateTime to` (both nullable; `null` = unbounded on that side) | T-5 |
| `RequestedLimit` (record) | `application.query` | `int value` | T-5 |
| `Cursor` (record) | `application.query` | `OffsetDateTime rt, UUID id, String fp` | T-6 |
| `KeysetPosition` (record) | `application.query` | `OffsetDateTime recordedAt, UUID id` | T-7 |
| `QueryPage` (record) | `application.query` | `List<AuditEvent> items, Optional<String> nextCursor` | T-7 |

Notes:
- `QueryCriteria` **does not** carry `limit` or `cursor`. They are
  passed separately on the use-case method so the fingerprint covers
  only substantive filters (per `design.md` §6 "Cursor format").
- `RequestedLimit` is a thin wrapper that gives the API a typed slot
  for `limit` and keeps `QueryCriteria` clean.

## 3. Ports and components

| Name | Package | Signature / kind | Owner task |
|---|---|---|---|
| `EventReader` (port interface) | `application.query` | `List<AuditEvent> read(QueryCriteria criteria, Optional<KeysetPosition> position, int rowLimit)` | T-7 declares; T-8 implements |
| `QueryCriteriaValidator` (class) | `application.query` | `void validate(QueryCriteria)` — throws `InvalidQueryException` | T-5 |
| `CursorCodec` (class) | `application.query` | `Cursor decode(String raw, String expectedFingerprint)` / `String encode(Cursor)` / `String fingerprintFor(QueryCriteria)` | T-6 |
| `QueryAuditEventsUseCase` (class) | `application.query` | `QueryPage execute(QueryCriteria criteria, RequestedLimit limit, Optional<String> rawCursor)` | T-7 |
| `JpaEventReader` (adapter impl) | `infrastructure.persistence.query` | implements `EventReader` | T-8 |

Notes:
- `CursorCodec.decode(raw, expectedFingerprint)` is a **single call**
  that decodes and verifies fingerprint binding in one shot. Callers
  do not perform a second verify step. This keeps cursor-binding
  un-skippable.
- `CursorCodec.fingerprintFor(QueryCriteria)` exposes the fingerprint
  so the use case can (a) pass `expectedFingerprint` into `decode`
  and (b) set `fp` on a freshly minted next-cursor.

## 4. Exception types

One class per family, with a `ProblemKind` enum nested inside the
class. Messages are static, fixed strings chosen by kind in the
constructor. They **never** include user-supplied input.

### `InvalidQueryException` (T-5, `application.query`)

```java
public final class InvalidQueryException extends RuntimeException {
    public enum ProblemKind { NO_FILTER, BLANK_FILTER, INVALID_TIME_RANGE }
    public ProblemKind kind();
    public Optional<String> field();   // present for BLANK_FILTER
}
```

### `InvalidCursorException` (T-6, `application.query`)

```java
public final class InvalidCursorException extends RuntimeException {
    public enum ProblemKind { INVALID_CURSOR, CURSOR_FILTER_MISMATCH }
    public ProblemKind kind();
}
```

`InvalidCursorException` collapses what early drafts split into
`InvalidCursorException` + `CursorFilterMismatchException`. Static
messages keyed by `ProblemKind` give the same leak-safety guarantee
with one type.

## 5. Problem type URIs

All under `https://audit-log-service/problems/`. Constants live in
`api.shared.ProblemTypes` (T-1 baseline, extended by T-10).

| Constant | URI slug | Source | Mapped by |
|---|---|---|---|
| `VALIDATION_ERROR` | `validation-error` | T-1 | generic `MethodArgumentNotValidException` fallback |
| `MALFORMED_REQUEST` | `malformed-request` | T-1 | `HttpMessageNotReadableException` / missing param |
| `INVALID_AUDIT_EVENT` | `invalid-audit-event` | T-1 | `InvalidAuditEventException` (domain, 400) |
| `INTERNAL_ERROR` | `internal-error` | T-1 | catch-all |
| `NO_FILTER` | `no-filter` | T-10 | `InvalidQueryException(NO_FILTER)` |
| `BLANK_FILTER` | `blank-filter` | T-10 | `InvalidQueryException(BLANK_FILTER)` and/or DTO `@AssertTrue` |
| `INVALID_OUTCOME` | `invalid-outcome` | T-10 | `MethodArgumentTypeMismatchException` (param=`outcome`) |
| `INVALID_TIMESTAMP` | `invalid-timestamp` | T-10 | `MethodArgumentTypeMismatchException` (param=`from`\|`to`) |
| `INVALID_TIME_RANGE` | `invalid-time-range` | T-10 | `InvalidQueryException(INVALID_TIME_RANGE)` or DTO `@AssertTrue` |
| `INVALID_LIMIT` | `invalid-limit` | T-10 | DTO `@Min(1) @Max(200)` on `limit` (field-routed by handler) |
| `INVALID_CURSOR` | `invalid-cursor` | T-10 | `InvalidCursorException(INVALID_CURSOR)` |
| `CURSOR_FILTER_MISMATCH` | `cursor-filter-mismatch` | T-10 | `InvalidCursorException(CURSOR_FILTER_MISMATCH)` |

`INVALID_LIMIT` is **not** on `InvalidQueryException.ProblemKind` —
limit-range violations come through `MethodArgumentNotValidException`
and are routed to `invalid-limit` by the handler's field-name switch.

## 6. HTTP request and response

### 6.1 Request parameter object

`api.query.QueryAuditEventsRequest` (T-9) is a Spring
`@ModelAttribute`-bound record. It carries bean-validation
annotations so T-10 can map field-level violations cleanly. Flat
`@RequestParam`s on the controller method are **not** used — they
would block `@AssertTrue` cross-field validators.

```java
public record QueryAuditEventsRequest(
    String actor,
    String resource,
    String eventType,           // bound from ?event_type=…
    Outcome outcome,
    OffsetDateTime from,
    OffsetDateTime to,
    @Min(1) @Max(200) Integer limit,   // null → use case applies default 50
    String cursor
) { /* @AssertTrue cross-field methods owned by T-10 */ }
```

Snake_case URL parameter binding: a `Converter<String, String>` or
field-level `@JsonAlias`/`@Name` is **not** sufficient for query
params. Use Spring's `@ConstructorBinding` with parameter renaming
via `@RequestParam`-style aliasing on the record component, or set
`spring.mvc.parameter-resolver-disabled=false` plus a property
naming strategy. T-9 picks the simplest working mechanism and
documents it.

### 6.2 Response envelope

`api.query.AuditEventQueryResponse` (T-9):

```java
public record AuditEventQueryResponse(
    List<AuditEventResponse> items,
    @JsonProperty("next_cursor") String nextCursor   // null on last page
) {}
```

T-7's `QueryPage.nextCursor()` returns `Optional<String>`. The
controller calls `.orElse(null)` when building the response.

Item snake_case fields mirror the persisted shape and use
`@JsonProperty` on each renamed field (T-9 keeps ingestion's
existing per-field convention; no global Jackson naming strategy
change).

## 7. Use-case execution order

`QueryAuditEventsUseCase.execute` MUST perform these steps in this
exact order so error precedence is predictable and tests can lock it:

1. `QueryCriteriaValidator.validate(criteria)` — throws
   `InvalidQueryException(NO_FILTER | BLANK_FILTER | INVALID_TIME_RANGE)`.
2. If `rawCursor.isPresent()`:
   `String expected = codec.fingerprintFor(criteria);`
   `Cursor decoded = codec.decode(rawCursor.get(), expected);`
   — throws `InvalidCursorException(INVALID_CURSOR | CURSOR_FILTER_MISMATCH)`.
3. Build `Optional<KeysetPosition>` from `decoded` (or `empty()`).
4. `List<AuditEvent> rows = reader.read(criteria, position, limit.value() + 1)`.
5. Split: `items = rows.subList(0, min(rows.size(), limit.value()))`;
   `hasNext = rows.size() > limit.value()`.
6. If `hasNext`: encode next cursor from the last item of `items`
   with `fp = expected` (or compute fresh if step 2 skipped). Else
   `nextCursor = Optional.empty()`.
7. Return `new QueryPage(items, nextCursor)`.

## 8. Branch / commit / PR naming

- Branch: `feature/query-api-T-<N>-<slug>`
- Commit (first line): `T-<N>: <imperative summary>`
- PR title: `T-<N>: <Sentence case summary>`

## 9. Change log

- **2026-05-11.** Initial version — extracted from cross-plan review
  of T-3..T-11. Resolves three reconciliation items:
  (a) `RequestedLimit` adopted; `QueryCriteria` does not carry
  `limit`; use case takes both as parameters.
  (b) Single-class exception types with nested `ProblemKind` enum,
  for both `InvalidQueryException` and `InvalidCursorException`.
  (c) `QueryAuditEventsRequest` request parameter object adopted
  in T-9 so T-10 can hang `@AssertTrue` / `@Min` / `@Max` validators.
