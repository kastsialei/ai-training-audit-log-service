# Feature specs

One Markdown file per feature, free-form. Conventional headings
(use what applies, omit what doesn't):

- **Goal** — one sentence: what changes for the consumer when this ships.
- **Non-goals** — what this spec deliberately does *not* cover.
- **API** — endpoints, request/response shape, error cases.
- **Data model** — new tables/columns; reference the migration filename.
- **Invariants** — what must always hold (e.g. immutability, ordering,
  UTC time). Cross-reference relevant glossary terms in `product.md`.
- **Open questions** — unresolved design points.

Filename: `<feature>.md` (lowercase, kebab-case). Example:
`event-ingestion.md`, `retention-policy.md`.

A spec is *not* an ADR. ADRs (`docs/adr/`) capture decisions about
*how the codebase is built*. Specs describe *what a feature does*.
A feature spec may cite ADRs and be cited by them.
