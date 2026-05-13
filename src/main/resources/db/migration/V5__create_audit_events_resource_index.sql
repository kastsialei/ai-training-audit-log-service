-- resource=? + keyset pagination.

CREATE INDEX idx_audit_events_resource_recorded_at_id
    ON audit_events (resource, recorded_at DESC, id DESC);
