# Architecture

This document is the long-form companion to `AGENTS.md`. It captures
layering, conventions, and the full repo map. For the *what* and *why* of
the product, see `product.md`. For the *how-we-decided*, see `docs/adr/`.

## Layering

```
   ┌─────────────────────────────────────────────┐
   │                  api                        │   HTTP, DTOs
   └────────────────────┬────────────────────────┘
                        │ depends on
                        ▼
   ┌─────────────────────────────────────────────┐
   │              application                    │   use cases, ports
   └────────────────────┬────────────────────────┘
                        │ depends on
                        ▼
   ┌─────────────────────────────────────────────┐
   │                domain                       │   entities, invariants
   └─────────────────────────────────────────────┘
                        ▲
                        │ implements ports
   ┌────────────────────┴────────────────────────┐
   │             infrastructure                  │   JPA repos, adapters
   └─────────────────────────────────────────────┘
```

**Dependencies point inward.** `api` and `infrastructure` know about
`application` and `domain`. `domain` knows nothing about anything else.
`application` knows `domain` only.

**Pragmatic exception (ADR-0004):** JPA entities live in `domain/` rather
than in a separate persistence model. The domain depends on
`jakarta.persistence`. This is a deliberate trade-off — see the ADR.

## Repo map (full)

```
audit-log-service/
├── AGENTS.md                       # agent entry point + short map
├── ARCHITECTURE.md                 # this file
├── product.md                      # product description + glossary
├── README.md                       # human runbook (build/run/deploy)
├── pom.xml                         # Maven build
├── mvnw, mvnw.cmd, .mvn/           # Maven wrapper
│
├── src/main/java/net/sam/ai/engineering/audit/
│   ├── AuditLogServiceApplication.java   # Spring Boot main
│   ├── domain/<feature>/                 # entities (JPA), value objects, domain services
│   ├── application/<feature>/            # use cases, ports (interfaces)
│   ├── infrastructure/
│   │   └── persistence/<feature>/        # Spring Data JPA repositories, adapters
│   └── api/<feature>/                    # @RestController, request/response DTOs
│
├── src/main/resources/
│   ├── application.yml                   # base config
│   ├── application-local.yml             # local profile overrides
│   ├── logback-spring.xml                # log format & MDC pattern
│   └── db/migration/V<n>__<snake>.sql    # Flyway migrations
│
├── src/test/java/net/sam/ai/engineering/audit/
│   ├── **/*Test.java                     # unit (Surefire, no Spring context)
│   └── **/*IT.java                       # integration (Failsafe, Testcontainers)
│
├── src/test/resources/
│   └── application-test.yml              # test profile (forces flyway + validate)
│
├── docs/
│   ├── adr/                              # MADR decisions (NNNN-kebab-title.md)
│   └── specs/                            # feature specs (free-form Markdown)
│
├── deploy/
│   ├── docker-compose.yml                # local Postgres
│   └── k8s/                              # ConfigMap, Secret, Deployment, Service, dev Postgres
│
└── .github/workflows/                    # CI (build + test + image publish)
```

## Package organization (ADR-0003)

Inside each layer, organize by **feature**:

```
domain/
  ingestion/   AuditEvent.java, EventType.java, Actor.java
  query/       EventFilter.java, ...
  shared/      (only if 2+ features depend on a type)
application/
  ingestion/   IngestEventUseCase.java, EventSink.java (port)
  query/       QueryEventsUseCase.java
api/
  ingestion/   IngestionController.java, IngestRequest.java, IngestResponse.java
  query/       QueryController.java
infrastructure/persistence/
  ingestion/   JpaEventSink.java, AuditEventRepository.java
```

Do not pre-create `shared/` packages. Promote a type to `shared/` only when
two or more features actually depend on it.

## Naming conventions

| Concept | Convention | Example |
|---|---|---|
| Use case | `<Verb><Noun>UseCase` | `IngestEventUseCase` |
| Port (interface owned by `application`) | noun describing capability | `EventSink`, `EventReader` |
| JPA entity | `<Noun>` (no `Entity` suffix) | `AuditEvent` |
| Spring Data repository | `<Entity>Repository` | `AuditEventRepository` |
| REST controller | `<Resource>Controller` | `IngestionController` |
| Request DTO | `<UseCase>Request` | `IngestEventRequest` |
| Response DTO | `<UseCase>Response` | `IngestEventResponse` |
| Unit test | `<ClassUnderTest>Test` | `AuditEventTest` |
| Integration test | `<Feature>IT` | `IngestionIT` |

DTOs live next to the controller they serve (`api/<feature>/`). Keep them
as Java `record`s.

## Cross-cutting conventions

### Time

- All timestamps are **UTC**, persisted as `TIMESTAMPTZ` (Postgres) /
  `OffsetDateTime` (Java).
- The server is the source of truth for **recorded-at**. Use a single
  `java.time.Clock` bean (`Clock.systemUTC()` by default; override in
  tests) — never `Instant.now()` directly in code under test.
- The producer-supplied **occurred-at** is preserved verbatim if present;
  validate it's parsable but do not "correct" it.

### Correlation ID & MDC

- Every accepted ingest carries a correlation ID. Read from the
  `X-Correlation-Id` request header; if absent, generate a UUID.
- Place into SLF4J `MDC` under key `correlationId` for the duration of
  the request. `logback-spring.xml` includes it in every log line.
- Propagate to outbound calls (none in v1, but the convention is set).

### Error handling

- Domain invariants throw `domain`-package exceptions (e.g.
  `InvalidEventTypeException`). Never throw `RuntimeException` directly.
- A single `@RestControllerAdvice` in `api/` translates domain
  exceptions to RFC 7807 `application/problem+json` responses.
- Ingestion failures are **never silent** — log at `ERROR` with the
  correlation ID and enough context to reconstruct the input.

### Logging

- JSON-structured output in non-local profiles. See `logback-spring.xml`.
- Standard MDC keys: `correlationId`, `eventType`, `sourceService`.
- Do not log entire event payloads at `INFO` — payloads may contain PII.
  Log identifiers and types; payloads only at `DEBUG` and only when
  explicitly enabled.

## Migration rules (Flyway)

- Filename: `V<n>__<snake_case_description>.sql`. Example:
  `V2__create_audit_events_table.sql`.
- `<n>` is a strictly increasing integer. No gaps, no re-numbering.
- **Never edit a migration that has been applied** to any environment
  (including CI). Add a new migration that performs the correction.
- Migrations are append-only DDL/DML — no destructive operations on
  existing audit data.
- Repeatable migrations (`R__*.sql`) are reserved for views/functions;
  not used for schema changes.
- Tests run with `spring.flyway.enabled=true` and
  `spring.jpa.hibernate.ddl-auto=validate` (see
  `src/test/resources/application-test.yml`) — drift between JPA model
  and migrations fails the build.

## Testing strategy

Pyramid: many unit tests, fewer integration tests, a small number of
acceptance tests later.

- **Unit (`*Test.java`)** — no Spring context, no DB. Cover domain logic
  and pure use-case logic. Use a fixed `Clock` for time-dependent paths.
- **Integration (`*IT.java`)** — Testcontainers Postgres, real Flyway,
  real Spring context. Cover wiring and persistence behaviour.
- **Coverage targets** (informal): domain 100% of branches, application
  ≥ 90% of lines, persistence/api covered by integration tests rather
  than unit tests.

## Where decisions live

- **Architecture decisions** that change *how the codebase is built* —
  ADRs in `docs/adr/`.
- **Product/scope decisions** that change *what the service does* —
  recorded in `product.md` (or, for individual features, in
  `docs/specs/<feature>.md`).
- **Conventions** (naming, layering, time, logging) — this file. When a
  convention changes, update both this file *and* an ADR explaining why.
