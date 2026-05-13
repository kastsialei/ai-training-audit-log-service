-- Time-only queries (from/to), and outcome-only recheck fallback.

CREATE INDEX idx_audit_events_recorded_at_id
    ON audit_events (recorded_at DESC, id DESC);
