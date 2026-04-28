-- Append-only store for audit events.
-- Server is source of truth for `id` (gen_random_uuid) and `recorded_at` (now()).
-- UPDATE / DELETE on this table are blocked by triggers (immutability invariant).

CREATE TYPE audit_outcome AS ENUM ('SUCCESS', 'DENIED', 'ERROR');

CREATE TABLE audit_events (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    recorded_at  TIMESTAMPTZ  NOT NULL    DEFAULT now(),
    actor        TEXT         NOT NULL,
    event_type   TEXT         NOT NULL,
    resource     TEXT         NOT NULL,
    outcome      audit_outcome,
    context      JSONB        NOT NULL    DEFAULT '{}'::jsonb,
    CONSTRAINT audit_events_actor_not_blank      CHECK (length(btrim(actor))      > 0),
    CONSTRAINT audit_events_event_type_not_blank CHECK (length(btrim(event_type)) > 0),
    CONSTRAINT audit_events_resource_not_blank   CHECK (length(btrim(resource))   > 0)
);

CREATE OR REPLACE FUNCTION audit_events_block_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'audit_events is append-only: % is not permitted', TG_OP
        USING ERRCODE = 'check_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_events_no_update
    BEFORE UPDATE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION audit_events_block_modification();

CREATE TRIGGER audit_events_no_delete
    BEFORE DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION audit_events_block_modification();
