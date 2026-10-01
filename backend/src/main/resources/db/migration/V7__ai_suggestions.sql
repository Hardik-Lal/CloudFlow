-- Phase 8: AI-generated artifacts awaiting (or after) user approval. Knowledge embeddings live in
-- the AI service's own "ai" schema.

CREATE TABLE ai_suggestions (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id     uuid         NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    environment_id uuid REFERENCES environments (id) ON DELETE CASCADE,
    type           varchar(20)  NOT NULL,
    status         varchar(20)  NOT NULL,
    file_path      varchar(255) NOT NULL,
    content        text         NOT NULL,
    explanation    text,
    instructions   varchar(2000),
    created_by     uuid REFERENCES users (id) ON DELETE SET NULL,
    reviewed_by    uuid REFERENCES users (id) ON DELETE SET NULL,
    reviewed_at    timestamptz,
    result         text,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_ai_suggestions_type CHECK (type IN ('DOCKERFILE', 'ENV_TEMPLATE', 'WORKFLOW', 'DOCUMENTATION')),
    CONSTRAINT ck_ai_suggestions_status CHECK (status IN ('PENDING', 'APPLIED', 'REJECTED'))
);

CREATE INDEX idx_ai_suggestions_project_created ON ai_suggestions (project_id, created_at DESC);
CREATE INDEX idx_ai_suggestions_environment ON ai_suggestions (environment_id);
CREATE INDEX idx_ai_suggestions_created_by ON ai_suggestions (created_by);
CREATE INDEX idx_ai_suggestions_reviewed_by ON ai_suggestions (reviewed_by);
