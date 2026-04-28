# ADR 0007 — Append-only enforced by Postgres triggers

- Status: Accepted
- Date: 2026-04-28

## Context and Problem Statement

`product.md` lists immutability as a core promise: "Events are never
updated or deleted by service consumers." The invariant must hold even
if a future service code path, an ad-hoc DBA script, or a misconfigured
ORM mapping issues an `UPDATE` / `DELETE`. Application-level guards
(JPA `@Immutable`, missing `update`/`delete` use cases) are necessary
but not sufficient: they only constrain code that goes through the
application.

## Decision Drivers

- "No silent failures" — a forbidden mutation must be loud and visible,
  not silently succeed.
- Defence in depth — the storage layer is the last line, and it sees
  every connection regardless of which service made the change.
- Cheap to add, cheap to remove — a trigger is one migration, no
  application-side code change required.

## Considered Options

1. **Application-only.** JPA `@Immutable` plus discipline (no save
   after insert, no `update*` use cases). DB schema unprotected.
2. **`REVOKE UPDATE, DELETE`** on the application role.
3. **`BEFORE UPDATE / DELETE` triggers** on `audit_events` that
   `RAISE EXCEPTION`.
4. **Triggers + revokes** combined.

## Decision Outcome

Chosen option: **Application-level `@Immutable` + Postgres `BEFORE
UPDATE / DELETE` triggers** (option 1 + option 3).

The trigger function `audit_events_block_modification()` raises
`check_violation` on any row-level `UPDATE` or `DELETE`. It runs
regardless of the connecting role, so it catches DBA shells,
migrations, and bypassed ORM paths.

`REVOKE` is **not** added in v1. Migrations themselves run as the same
role as the application in the current deployment; revoking would
either need a separate migration role or block the `flyway_schema_history`
bookkeeping. The trigger is sufficient for the invariant; per-role
permissions can be layered on later without re-reading this decision.

## Consequences

- Adding columns to `audit_events` is still possible via DDL `ALTER
  TABLE` (DDL is not row-level and does not fire row triggers).
- A future "soft retention" feature that legitimately needs to delete
  rows — e.g. expiry of events older than N days — must run as a role
  the trigger explicitly allows, or the trigger must be extended with
  a `current_setting('audit.allow_purge')` check. Either way, the
  exception is explicit, not accidental.
- `TRUNCATE` is **not** caught by row-level triggers. If this becomes a
  concern, add a statement-level `BEFORE TRUNCATE` trigger in a follow-up
  migration.
- The integration test `IngestionIT#databaseRejectsUpdateAndDelete`
  exercises the trigger directly through a raw `DataSource`, so any
  regression breaks the build.
