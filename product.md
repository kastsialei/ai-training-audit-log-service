# Audit Log Service — Product

Internal service that ingests audit events from other company services and
stores them immutably. Required for compliance, security, and observability.

## Consumers

- **Compliance officers** — read events to demonstrate regulatory adherence.
- **SRE** — correlate operational incidents with system actions.
- **Security analysts** — investigate suspicious behaviour and reconstruct
  timelines.

## Core promises

- **Append-only.** Events are never updated or deleted by service consumers.
- **Traceable.** Every accepted write is observable (logs, correlation IDs).
- **Authoritative time.** Server stamps the canonical UTC timestamp.
- **Loud failures.** Ingestion failures must not be silent.

## Non-goals

- Not a general logging pipeline (no app/infra logs).
- Not a metric store (no aggregations, no time-series rollups).
- Not a search engine (structured query, not free-text relevance).
- Not a forwarder (no fan-out to external SIEMs in v1).

## Glossary (ubiquitous language)

Use these terms exactly — in code, specs, ADRs, and commit messages.

| Term | Meaning |
|---|---|
| **Audit event** | One immutable record describing something that happened. The unit of ingestion and the unit of read. |
| **Event type** | Discriminator naming what kind of thing happened (e.g. `user.login`, `policy.updated`). Stable, namespaced, lowercased. |
| **Actor** | The principal that caused the event (user, service account, automated job). Has an identifier and a kind. |
| **Subject** | The entity the event is *about* (the resource acted upon). Distinct from actor. |
| **Source service** | The service that produced and submitted the event. Identified by a stable name. |
| **Correlation ID** | Identifier that ties this event to a broader request/trace across services. |
| **Occurred-at** | Wall-clock time, in UTC, when the event happened in the source system. Supplied by the producer. |
| **Recorded-at** | UTC timestamp assigned by this service when the event is durably persisted. Authoritative for ordering. |
| **Ingest** | The act of accepting an event over the API and durably persisting it. |
| **Append** | Persistence-level term: writing a new row; never an update or delete. |
| **Immutability** | Invariant that a recorded event is never modified or removed by consumers. Retention/expiry is a separate concern. |
| **Retention** | Policy-driven lifecycle for events (out of scope for v1; mentioned here so the term is reserved). |

Avoid: *log entry*, *audit record*, *trace*, *message* — all collide with
adjacent concepts. If you need a new term, add it here first.

## See also

- `ARCHITECTURE.md` — layering, conventions, full repo map.
- `docs/adr/` — architecture decisions.
- `docs/specs/` — feature specs.
