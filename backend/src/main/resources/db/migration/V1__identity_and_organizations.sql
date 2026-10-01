-- Phase 2: users, GitHub credentials, refresh tokens, organizations, and memberships.

CREATE TABLE users (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    github_id     bigint       NOT NULL,
    username      varchar(100) NOT NULL,
    email         varchar(320),
    display_name  varchar(255),
    avatar_url    varchar(1024),
    last_login_at timestamptz,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uk_users_github_id UNIQUE (github_id)
);

-- GitHub logins are case-insensitive; lookups by username use lower(username).
CREATE UNIQUE INDEX uk_users_username_lower ON users (lower(username));

CREATE TABLE user_github_credentials (
    user_id                uuid PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    access_token_encrypted text         NOT NULL,
    scopes                 varchar(500) NOT NULL DEFAULT '',
    created_at             timestamptz  NOT NULL DEFAULT now(),
    updated_at             timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE refresh_tokens (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash varchar(64) NOT NULL,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);

CREATE TABLE organizations (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name       varchar(100) NOT NULL,
    slug       varchar(50)  NOT NULL,
    created_by uuid REFERENCES users (id) ON DELETE SET NULL,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uk_organizations_slug UNIQUE (slug)
);

CREATE INDEX idx_organizations_created_by ON organizations (created_by);

CREATE TABLE organization_memberships (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id uuid        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    user_id         uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role            varchar(20) NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_memberships_org_user UNIQUE (organization_id, user_id),
    CONSTRAINT ck_memberships_role CHECK (role IN ('OWNER', 'ADMIN', 'DEVELOPER', 'VIEWER'))
);

CREATE INDEX idx_memberships_user_id ON organization_memberships (user_id);
