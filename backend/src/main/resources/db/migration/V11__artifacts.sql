-- Phase 10: metadata of objects kept in S3 (or S3-compatible storage): build artifacts, generated
-- files, and archived deployment logs. The content lives in the bucket under storage_key.

CREATE TABLE artifacts (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id     uuid         NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    environment_id uuid         REFERENCES environments (id) ON DELETE SET NULL,
    deployment_id  uuid         REFERENCES deployments (id) ON DELETE SET NULL,
    kind           varchar(20)  NOT NULL,
    name           varchar(255) NOT NULL,
    storage_key    varchar(512) NOT NULL UNIQUE,
    content_type   varchar(100) NOT NULL,
    size_bytes     bigint       NOT NULL,
    sha256         varchar(64)  NOT NULL,
    created_by     uuid         REFERENCES users (id) ON DELETE SET NULL,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_artifacts_kind CHECK (kind IN ('BUILD_ARTIFACT', 'GENERATED_FILE', 'ARCHIVED_LOG'))
);

CREATE INDEX idx_artifacts_project_time ON artifacts (project_id, created_at DESC);
CREATE INDEX idx_artifacts_deployment ON artifacts (deployment_id);
