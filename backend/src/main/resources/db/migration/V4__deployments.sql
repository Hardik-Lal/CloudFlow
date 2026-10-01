-- Phase 5: deployments and their logs.

CREATE TABLE deployments (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id      uuid         NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    environment_id  uuid         NOT NULL REFERENCES environments (id) ON DELETE CASCADE,
    triggered_by    uuid REFERENCES users (id) ON DELETE SET NULL,
    trigger_type    varchar(20)  NOT NULL,
    status          varchar(20)  NOT NULL,
    branch          varchar(255) NOT NULL,
    commit_sha      varchar(40),
    commit_message  varchar(500),
    app_type        varchar(20),
    image_tag       varchar(500),
    image_id        varchar(100),
    container_id    varchar(100),
    container_name  varchar(200),
    host_port       integer,
    rollback_of_id  uuid REFERENCES deployments (id) ON DELETE SET NULL,
    -- The deployment whose container currently serves the environment.
    active          boolean      NOT NULL DEFAULT false,
    failure_reason  text,
    started_at      timestamptz,
    finished_at     timestamptz,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_deployments_trigger_type CHECK (trigger_type IN ('MANUAL', 'PIPELINE', 'ROLLBACK')),
    CONSTRAINT ck_deployments_status CHECK (status IN (
        'QUEUED', 'BUILDING', 'DEPLOYING', 'HEALTH_CHECK',
        'SUCCEEDED', 'FAILED', 'CANCELLED', 'ROLLED_BACK'))
);

CREATE INDEX idx_deployments_environment_created ON deployments (environment_id, created_at DESC);
CREATE INDEX idx_deployments_project_created ON deployments (project_id, created_at DESC);
CREATE INDEX idx_deployments_rollback_of ON deployments (rollback_of_id);
CREATE INDEX idx_deployments_triggered_by ON deployments (triggered_by);

-- At most one in-flight deployment per environment, enforced by the database.
CREATE UNIQUE INDEX uk_deployments_environment_in_progress ON deployments (environment_id)
    WHERE status IN ('QUEUED', 'BUILDING', 'DEPLOYING', 'HEALTH_CHECK');

-- At most one active (serving) deployment per environment.
CREATE UNIQUE INDEX uk_deployments_environment_active ON deployments (environment_id) WHERE active;

CREATE TABLE deployment_logs (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    deployment_id uuid        NOT NULL REFERENCES deployments (id) ON DELETE CASCADE,
    phase         varchar(20) NOT NULL,
    level         varchar(10) NOT NULL,
    message       text        NOT NULL,
    logged_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_deployment_logs_phase CHECK (phase IN ('SOURCE', 'BUILD', 'PUSH', 'DEPLOY', 'HEALTH_CHECK', 'RUNTIME')),
    CONSTRAINT ck_deployment_logs_level CHECK (level IN ('INFO', 'WARN', 'ERROR'))
);

CREATE INDEX idx_deployment_logs_deployment ON deployment_logs (deployment_id, id);
