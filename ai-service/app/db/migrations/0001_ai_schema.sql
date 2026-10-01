-- Knowledge base for retrieval-augmented answers. Owned by the AI service (schema "ai").
CREATE EXTENSION IF NOT EXISTS vector;
CREATE SCHEMA IF NOT EXISTS ai;

-- One indexed source: a repository file, configuration, deployment record, or log excerpt.
CREATE TABLE ai.documents (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id     uuid         NOT NULL,
    source_type    varchar(30)  NOT NULL,
    source_ref     varchar(500) NOT NULL,
    environment_id uuid,
    deployment_id  uuid,
    content_hash   char(64)     NOT NULL,
    metadata       jsonb        NOT NULL DEFAULT '{}',
    indexed_at     timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uk_documents_project_source UNIQUE (project_id, source_ref)
);

CREATE INDEX idx_documents_project ON ai.documents (project_id);

CREATE TABLE ai.chunks (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    document_id uuid         NOT NULL REFERENCES ai.documents (id) ON DELETE CASCADE,
    chunk_index integer      NOT NULL,
    content     text         NOT NULL,
    embedding   vector(1536) NOT NULL
);

CREATE INDEX idx_chunks_document ON ai.chunks (document_id);
CREATE INDEX idx_chunks_embedding ON ai.chunks USING hnsw (embedding vector_cosine_ops);
