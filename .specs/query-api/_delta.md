# Spec Delta Log — query-api

### 2026-05-11 (pass 2): InvalidAuditEventException → 400

- Новый AC в US-4: невалидные `POST /audit-events` payload (domain validation) → 400, не 500
- design §7 Prerequisites: `InvalidAuditEventException` добавлен в маппинг global advice
- design §10 Risk: явно назван переход 500 → 400 наряду с изменением shape
- tasks T-1 Scope: новый bullet "Map `InvalidAuditEventException` to `400`"
- tasks T-1 DoD: failing-first integration тест на 500 → 400 миграцию

---

## Было в design, не было в requirements → убрано (pass 1, 2026-05-11)

- p99 latency SLO ("bounded", "off full scans") — перенесено в §Out of scope
- Unknown query parameters ignored — перенесено в §Out of scope
- API versioning strategy (`/v2/audit-events`) — перенесено в §Out of scope
- Cursor "no expiry" bullet — перенесено в §Out of scope
- Downstream availability (503) subsection — перенесено в §Out of scope
- Observability subsection (metrics, structured logs) — перенесено в §Out of scope; оставлен только pointer на correlation-ID filter
- Latency/throughput/operability bullets — перенесены в §Out of scope; заменены одной строкой "deferred to SRE"

---

## Было в tasks, не было в design/requirements → убрано (pass 1, 2026-05-11)

- T-12 "Add Query Observability" — orphan после trimming design §7/§8; удалён из overview table, coverage table, и Correlation-ID coverage row
