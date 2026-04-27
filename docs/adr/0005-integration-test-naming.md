# ADR 0005 — Integration tests use the `*IT` suffix

- Status: Accepted
- Date: 2026-04-27

## Context and Problem Statement

We have two kinds of tests: fast unit tests (no Spring, no DB) and
integration tests (Spring context + Testcontainers Postgres). They have
very different runtime profiles and should be runnable separately.
Maven splits them via the Surefire (unit, `mvn test`) and Failsafe
(integration, `mvn verify`) plugins, which key off filename patterns.

## Decision Drivers

- Agents need a deterministic answer to "where does this new test go
  and what should I name it?"
- Surefire and Failsafe should not double-execute the same test.
- We don't want to maintain Maven plugin configuration that duplicates
  defaults.

## Considered Options

1. **Naming-only convention, default suffixes.** Unit `*Test.java`,
   integration `*IT.java`. Both plugins pick up their defaults; no
   plugin configuration overrides.
2. **Naming-only, custom suffix `*IntegrationTest`.** Requires
   `<includes>` override in Failsafe. Slightly more readable name.
3. **Separate source roots** (`src/integrationTest/java`). More
   ceremony; requires `build-helper-maven-plugin` or similar.

## Decision Outcome

Chosen option: **Naming-only with default suffixes (`*Test`, `*IT`)**.
Both files live in `src/test/java/...`. No plugin includes override.

## Consequences

- `pom.xml` Failsafe configuration carries no `<includes>` block —
  defaults apply.
- New unit test → `FooTest.java`. New integration test → `FooIT.java`.
- `mvn test` runs only unit tests; `mvn verify` runs both.
- Existing `HealthIntegrationTest.java` was renamed to `HealthIT.java`
  on adoption.
