-- event_type=? + keyset pagination.

CREATE INDEX idx_audit_events_event_type_recorded_at_id
    ON audit_events (event_type, recorded_at DESC, id DESC);
