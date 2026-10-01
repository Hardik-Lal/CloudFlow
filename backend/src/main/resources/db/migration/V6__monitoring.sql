-- Phase 7: health probe results and environment events (metrics themselves live in Prometheus).

CREATE TABLE health_check_results (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    environment_id   uuid        NOT NULL REFERENCES environments (id) ON DELETE CASCADE,
    deployment_id    uuid        NOT NULL REFERENCES deployments (id) ON DELETE CASCADE,
    healthy          boolean     NOT NULL,
    status_code      integer,
    response_time_ms integer     NOT NULL,
    error            varchar(500),
    checked_at       timestamptz NOT NULL
);

CREATE INDEX idx_health_checks_environment_time ON health_check_results (environment_id, checked_at DESC);
CREATE INDEX idx_health_checks_deployment ON health_check_results (deployment_id);

CREATE TABLE environment_events (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    environment_id uuid         NOT NULL REFERENCES environments (id) ON DELETE CASCADE,
    deployment_id  uuid REFERENCES deployments (id) ON DELETE SET NULL,
    type           varchar(40)  NOT NULL,
    severity       varchar(10)  NOT NULL,
    message        varchar(1000) NOT NULL,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_environment_events_severity CHECK (severity IN ('INFO', 'WARN', 'ERROR'))
);

CREATE INDEX idx_environment_events_environment_time ON environment_events (environment_id, created_at DESC);
CREATE INDEX idx_environment_events_deployment ON environment_events (deployment_id);
