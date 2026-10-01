-- Phase 3: projects created from GitHub repositories, with repository and branch metadata.

CREATE TABLE projects (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    name            varchar(100) NOT NULL,
    slug            varchar(50)  NOT NULL,
    description     varchar(500),
    app_type        varchar(20)  NOT NULL,
    created_by      uuid REFERENCES users (id) ON DELETE SET NULL,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uk_projects_org_slug UNIQUE (organization_id, slug),
    CONSTRAINT ck_projects_app_type CHECK (
        app_type IN ('JAVA_MAVEN', 'JAVA_GRADLE', 'NODE', 'PYTHON', 'DOCKERFILE', 'UNKNOWN'))
);

CREATE INDEX idx_projects_created_by ON projects (created_by);

CREATE TABLE repositories (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id     uuid          NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    github_repo_id bigint        NOT NULL,
    owner          varchar(100)  NOT NULL,
    name           varchar(100)  NOT NULL,
    full_name      varchar(201)  NOT NULL,
    html_url       varchar(1024) NOT NULL,
    clone_url      varchar(1024) NOT NULL,
    default_branch varchar(255)  NOT NULL,
    private        boolean       NOT NULL,
    language       varchar(100),
    last_synced_at timestamptz   NOT NULL,
    created_at     timestamptz   NOT NULL DEFAULT now(),
    updated_at     timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT uk_repositories_project_id UNIQUE (project_id)
);

CREATE INDEX idx_repositories_github_repo_id ON repositories (github_repo_id);

CREATE TABLE branches (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    repository_id   uuid         NOT NULL REFERENCES repositories (id) ON DELETE CASCADE,
    name            varchar(255) NOT NULL,
    head_commit_sha varchar(40)  NOT NULL,
    protected       boolean      NOT NULL DEFAULT false,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uk_branches_repository_name UNIQUE (repository_id, name)
);
