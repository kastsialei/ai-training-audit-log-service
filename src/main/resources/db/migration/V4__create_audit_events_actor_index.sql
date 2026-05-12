-- actor=? + keyset pagination.

CREATE INDEX idx_audit_events_actor_recorded_at_id
    ON audit_events (actor, recorded_at DESC, id DESC);
