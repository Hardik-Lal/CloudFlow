-- Phase 9: append-only audit trail of security-relevant actions.

CREATE TABLE audit_logs (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organization_id uuid REFERENCES organizations (id) ON DELETE CASCADE,
    actor_id        uuid REFERENCES users (id) ON DELETE SET NULL,
    actor_username  varchar(100),
    action          varchar(60)  NOT NULL,
    resource_type   varchar(40)  NOT NULL,
    resource_id     varchar(100),
    details         jsonb        NOT NULL DEFAULT '{}',
    ip_address      varchar(64),
    created_at      timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_logs_org_time ON audit_logs (organization_id, created_at DESC);
CREATE INDEX idx_audit_logs_actor ON audit_logs (actor_id);
CREATE INDEX idx_audit_logs_action ON audit_logs (action);
