# Project map
Внутренний сервис, который принимает аудит-события от других сервисов компании и хранит их immutable. 
Нужен для compliance, security, observability. Читают его compliance-офицеры, SRE, security-аналитики.

# Tech stack
- Java 21
- Spring Boot 3
- PostgreSQL 17 + Flyway
- Docker
- JUnit 5
- Testcontainers

# Invariants

## Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**
Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**
- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"


## Architecture rules

### 1. DDD-first approach
- Domain layer has no dependencies on frameworks
- EF Core and API concerns are isolated in Infrastructure/API layers
- Business rules live in the domain model

### 2. Clean architecture layering
- Domain → Application → Infrastructure → API
- Dependencies only point inward

### 3. Persistence rules
- EF Core used only in Infrastructure
- Schema changes only via migrations
- Data model optimized for append-only writes

### 4. Read/write separation
- Write model: append-only audit event store
- Read model: optimized queries for filtering/search
- CQRS-style separation allowed when needed

### 5. Time consistency
- All timestamps are UTC
- Server is the single source of truth for time

### 6. Retention & archival
- Background job removes or archives events older than N days
- Archived data stored separately from active query store

### 7. Observability
- Every write must be traceable (logging + correlation ID recommended)
- Failures in event ingestion must not be silent

### 8. Testing strategy
- Follow test-pymide : unit>integration>acceptance
- Testcontainers used for real PostgreSQL integration tests
- Domain logic fully covered by unit tests
- Critical API flows covered by integration tests

