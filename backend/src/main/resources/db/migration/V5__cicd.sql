-- Phase 6: GitHub Actions pipelines, their runs and jobs, and environment deploy tokens.

CREATE TABLE pipelines (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id         uuid         NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    environment_id     uuid         NOT NULL REFERENCES environments (id) ON DELETE CASCADE,
    workflow_path      varchar(255) NOT NULL,
    template           varchar(20)  NOT NULL,
    github_workflow_id bigint,
    committed_sha      varchar(40),
    created_by         uuid REFERENCES users (id) ON DELETE SET NULL,
    last_synced_at     timestamptz,
    created_at         timestamptz  NOT NULL DEFAULT now(),
    updated_at         timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uk_pipelines_environment UNIQUE (environment_id),
    CONSTRAINT uk_pipelines_project_workflow UNIQUE (project_id, workflow_path),
    CONSTRAINT ck_pipelines_template CHECK (template IN ('JAVA', 'NODE', 'PYTHON', 'DOCKER'))
);

CREATE INDEX idx_pipelines_created_by ON pipelines (created_by);

CREATE TABLE pipeline_runs (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    pipeline_id    uuid          NOT NULL REFERENCES pipelines (id) ON DELETE CASCADE,
    github_run_id  bigint        NOT NULL,
    run_number     integer       NOT NULL,
    run_attempt    integer       NOT NULL DEFAULT 1,
    event          varchar(50)   NOT NULL,
    status         varchar(30)   NOT NULL,
    conclusion     varchar(30),
    head_branch    varchar(255),
    head_sha       varchar(40)   NOT NULL,
    commit_message varchar(500),
    actor          varchar(100),
    html_url       varchar(1024) NOT NULL,
    started_at     timestamptz,
    completed_at   timestamptz,
    created_at     timestamptz   NOT NULL DEFAULT now(),
    updated_at     timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT uk_pipeline_runs_github_run UNIQUE (github_run_id)
);

CREATE INDEX idx_pipeline_runs_pipeline_number ON pipeline_runs (pipeline_id, run_number DESC);

CREATE TABLE pipeline_run_jobs (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id        uuid          NOT NULL REFERENCES pipeline_runs (id) ON DELETE CASCADE,
    github_job_id bigint        NOT NULL,
    name          varchar(255)  NOT NULL,
    status        varchar(30)   NOT NULL,
    conclusion    varchar(30),
    html_url      varchar(1024),
    started_at    timestamptz,
    completed_at  timestamptz,
    created_at    timestamptz   NOT NULL DEFAULT now(),
    updated_at    timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT uk_pipeline_run_jobs_github_job UNIQUE (github_job_id)
);

CREATE INDEX idx_pipeline_run_jobs_run ON pipeline_run_jobs (run_id);

CREATE TABLE deploy_tokens (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    environment_id uuid         NOT NULL REFERENCES environments (id) ON DELETE CASCADE,
    name           varchar(100) NOT NULL,
    token_hash     varchar(64)  NOT NULL,
    token_prefix   varchar(12)  NOT NULL,
    created_by     uuid REFERENCES users (id) ON DELETE SET NULL,
    last_used_at   timestamptz,
    revoked_at     timestamptz,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uk_deploy_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX idx_deploy_tokens_environment ON deploy_tokens (environment_id);
CREATE INDEX idx_deploy_tokens_created_by ON deploy_tokens (created_by);

-- The GitHub Actions run that triggered a pipeline deployment.
ALTER TABLE deployments ADD COLUMN pipeline_run_id bigint;
