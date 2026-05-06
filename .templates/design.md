# Design: <feature name>

> Filled in after `requirements.md` is locked and clarifications resolved.
>
> **How to use this template.** Each block below starts with questions an
> architect should ask. Answer the ones that apply to this feature. If a
> question is irrelevant, skip it. If an answer is non-trivial, promote it
> into a proper section. If trivial or N/A, write one line ("N/A — no public
> API, internal only") and move on. The final doc should contain only what
> matters for *this* feature — delete unused blocks.

- Requirements: <link to requirements.md>
- Related ADRs / prior designs: <links or "none">

---

## 1. Framing

Answer briefly — these set scope before diving into details.

- What problem from requirements is this solving, in one sentence?
- What is the smallest change that solves it? What are we deliberately *not*
  building (and why)?
- Are there constraints not in requirements? (deadlines, infra limits,
  team capacity, dependencies on other work)
- Who are the consumers of this feature? (humans, services, internal vs
  external)

## 2. Public surface / contract

Skip if there is no externally visible surface (purely internal refactor).

- Is there an API, CLI, library function, event, or message? What shape?
- Who calls it? Sync or async? Auth model?
- What are the success and error responses? Status codes, error shapes?
- Is this a new contract or a change to an existing one? If change:
  backwards compatible? Versioned? Deprecation path?
- Is idempotency required? How is it keyed?

→ If non-trivial, expand into **API / interface** with concrete examples
(request/response samples, signatures, schemas).

## 3. Data

Skip if no persistence change.

- New tables? Modified tables? New columns?
- Types, nullability, defaults, FKs, indexes — what's required vs nice-to-have?
- Migration strategy: online? backfill needed? estimated row count / runtime?
- Retention / archival? Storage growth?
- Are there read patterns that need an index that isn't obvious from writes?

→ If non-trivial, expand into **Data model** with column-level table or DDL
sketch.

## 4. Validation rules

Skip if inputs are trivial or already covered by type system.

- Per-field rules: lengths, formats, ranges, allowed enums, regex?
- Cross-field rules? Conditional requiredness?
- Where is each rule enforced — DB constraint, app code, both? Why?
- Normalization (trimming, casing, encoding) — at what layer?
- What does a validation failure return to the caller? (error shape, code)

→ If non-trivial, expand into **Validation rules** with rule → error mapping.

## 5. Invariants

The properties that must always hold. These are the contract for callers and
the basis for tests.

- What invariants does this feature introduce or rely on?
  (e.g. "an event once written is never modified", "sum of X equals Y")
- For each: how is it enforced? (DB constraint, trigger, app code, type
  system, test-only)
- What breaks downstream if an invariant is violated?

→ If you can't list at least one invariant, ask: is this feature actually
just CRUD with no guarantees? That's fine — say so explicitly.

## 6. Edge cases & failure modes

Walk through these explicitly. Each one you can rule out is one less surprise
in production.

- Empty / null / missing / malformed input — what happens?
- Duplicate submissions / replays — accepted? rejected? idempotent?
- Concurrent writers on the same resource — last-write-wins? optimistic lock?
- Partial failure mid-operation — what state is left behind? recovery path?
- Oversize payloads, pagination, unbounded inputs — limits and behavior at
  the limit?
- Time / clock concerns — UTC? client vs server time? skew?
- Downstream dependency unavailable — fail-open, fail-closed, retry, queue?

→ Expand into **Edge cases** the ones with non-default answers; one-liner
the rest.

## 7. Integration points

Skip if this feature is self-contained.

- What other systems does this read from / write to / publish to?
- Sync calls vs async events? Ordering guarantees?
- Timeout / retry policy? Circuit breaker?
- What happens if an integration partner is down or slow?
- Observability: what metrics / logs / traces does this need to be
  debuggable in prod?

## 8. Non-functional requirements

Only list what has a real target or constraint. Don't list "should be fast".

- Performance: throughput / latency targets, expected load?
- Security: authn/authz, PII handling, encryption at rest/in transit, audit?
- Compliance: regulatory or internal policy that constrains the design?
- Operability: alerts, runbooks, on-call impact?

## 9. Alternatives considered

For non-obvious design choices only — pick the top 1–3.

- Option → rejected because <reason>. (Saves future readers from
  re-litigating.)

## 10. Risk & rollout

- What's the blast radius if this ships broken?
- Feature flag? Gradual rollout? Dark launch?
- Rollback plan — can we revert cleanly, or does data shape change?

## 11. Test strategy

How we prove the contract and invariants hold. Map back to acceptance
criteria from `requirements.md`.

- Unit: <what logic/branches>
- Integration / IT: <DB, HTTP, real dependencies>
- Property / invariant tests: <if applicable>
- Manual / exploratory: <if anything can't be automated>

## 12. Open questions

- [ ] <Question still unresolved> — owner: <who>, blocks: <what>
