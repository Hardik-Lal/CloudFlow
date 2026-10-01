-- Phase 4: environments, environment variables (secrets encrypted at rest), deployment configuration.

CREATE TABLE environments (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id uuid         NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    type       varchar(20)  NOT NULL,
    branch     varchar(255) NOT NULL,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uk_environments_project_type UNIQUE (project_id, type),
    CONSTRAINT ck_environments_type CHECK (type IN ('DEVELOPMENT', 'STAGING', 'PRODUCTION'))
);

CREATE TABLE environment_variables (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    environment_id uuid         NOT NULL REFERENCES environments (id) ON DELETE CASCADE,
    key            varchar(128) NOT NULL,
    -- Plain text for regular variables; AES-256-GCM ciphertext ("v1:...") when secret = true.
    value          text         NOT NULL,
    secret         boolean      NOT NULL DEFAULT false,
    updated_by     uuid REFERENCES users (id) ON DELETE SET NULL,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uk_environment_variables_env_key UNIQUE (environment_id, key),
    CONSTRAINT ck_environment_variables_key CHECK (key ~ '^[A-Z_][A-Z0-9_]*$')
);

CREATE TABLE deployment_configs (
    environment_id    uuid PRIMARY KEY REFERENCES environments (id) ON DELETE CASCADE,
    template          varchar(20)  NOT NULL,
    runtime_version   varchar(20),
    build_command     varchar(1000),
    start_command     varchar(1000),
    dockerfile_path   varchar(255) NOT NULL DEFAULT 'Dockerfile',
    container_port    integer      NOT NULL,
    health_check_path varchar(255) NOT NULL DEFAULT '/',
    cpu_limit         numeric(4, 2),
    memory_limit_mb   integer,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_at        timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_deployment_configs_template CHECK (template IN ('JAVA', 'NODE', 'PYTHON', 'DOCKER')),
    CONSTRAINT ck_deployment_configs_port CHECK (container_port BETWEEN 1 AND 65535)
);
