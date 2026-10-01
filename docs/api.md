# CloudFlow — API Contracts

The backend is the only public API. Browsers never call the AI service directly.

- Base path: `/api/v1`
- Format: JSON (`application/json`). Errors use `application/problem+json`.
- Authentication: `Authorization: Bearer <access JWT>`, except where marked **public**.
- Identifiers: UUID strings. Timestamps: ISO-8601 UTC (`2026-09-25T10:15:30Z`).
- Field naming: `camelCase`.

## Conventions

### Status codes

| Code | Meaning |
| --- | --- |
| 200 / 201 / 204 | Success / created (with `Location` header) / no content |
| 202 | Accepted: async work started (deployments, analyses) |
| 400 | Validation failed (`errors[]` lists field problems) |
| 401 | Missing, invalid, or expired access token |
| 403 | Authenticated but lacking the required permission |
| 404 | Resource not found, **or** not visible to the caller (resources in other organizations are never revealed) |
| 409 | Conflict: duplicate slug or key, invalid state transition |
| 422 | Semantically invalid request, e.g. a deployment configuration that fails validation |
| 429 | Rate limit exceeded (`Retry-After` header) |
| 502 / 503 | Upstream (GitHub, Docker, AI service) failed or is unavailable |

### Error body (RFC 9457)

```json
{
  "type": "urn:cloudflow:problem:validation-error",
  "title": "Validation failed",
  "status": 400,
  "detail": "Request contains invalid fields",
  "instance": "/api/v1/organizations",
  "errors": [{ "field": "name", "message": "must not be blank" }]
}
```

Problem types: `urn:cloudflow:problem:` + `validation-error` | `not-found` | `conflict` |
`unprocessable` | `unauthorized` | `forbidden` | `upstream-error` | `rate-limited` | `internal-error`.

### Rate limits (Phase 9)

Every `/api/**` request passes through a per-client token bucket. The client is the signed-in user
for authenticated calls, the deploy token for CI hooks, and the IP address otherwise. When the
bucket is empty the API returns `429` with a `Retry-After` header (seconds) and a `rate-limited`
problem body.

| Bucket | Applies to | Default (per minute) | Setting |
| --- | --- | --- | --- |
| `auth` | `/api/v1/auth/**` (per IP) | 30 | `cloudflow.rate-limit.auth-per-minute` |
| `ai` | `POST` assistant query, knowledge reindex, suggestions, deployment analysis | 20 | `…ai-per-minute` |
| `hook` | `/api/v1/pipeline-hooks/**` (per deploy token) | 60 | `…pipeline-hook-per-minute` |
| `webhook` | `/api/v1/webhooks/**` (per IP) | 300 | `…webhook-per-minute` |
| `api` | everything else | 600 | `…api-per-minute` |

Buckets are held in memory per backend instance (see architecture §3 on horizontal scaling).

### Security headers (Phase 9)

API responses carry `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`,
`X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, and
`Cache-Control: no-store`. The frontend sets its own CSP in `next.config.ts`.

### Pagination

List endpoints that can grow accept `?page=0&size=20&sort=createdAt,desc` (max `size` 100) and return:

```json
{ "content": [], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0 }
```

## Authentication — Phase 2

| Method | Path | Auth | Description |
| --- | --- | --- | --- |
| GET | `/oauth2/authorization/github` | public | Starts the GitHub OAuth flow (redirects to GitHub). |
| GET | `/login/oauth2/code/github` | public | OAuth callback. Sets the `cloudflow_refresh` HttpOnly cookie and redirects to `${FRONTEND_URL}/auth/callback`. On failure it redirects to `${FRONTEND_URL}/login?error=oauth_failed`. |
| POST | `/api/v1/auth/refresh` | cookie | Rotates the refresh token and returns a new access token. |
| POST | `/api/v1/auth/logout` | cookie | Revokes the refresh token and clears the cookie. `204`. |
| GET | `/api/v1/users/me` | bearer | Current user profile. |

`POST /auth/refresh` → `200`

```json
{
  "accessToken": "eyJ…",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": { "id": "…", "username": "octocat", "displayName": "The Octocat", "email": "…", "avatarUrl": "…" }
}
```

The refresh cookie is `HttpOnly; SameSite=Strict; Path=/api/v1/auth` and `Secure` when
`CLOUDFLOW_COOKIE_SECURE=true`. It lasts 7 days and rotates on every refresh. If a rotated token is
presented again more than 10 seconds after rotation, it is treated as stolen and all of the user's
sessions are revoked. The 10-second grace period covers concurrent refreshes from several tabs.

Access-token claims (HS256): `sub` (user id), `username`, `iat`, `exp` (15 minutes), `iss = cloudflow`. Organization roles
are **not** stored in the token. They are looked up for each request, so a role change takes effect
immediately.

## Organizations and members — Phase 2

| Method | Path | Permission | Description |
| --- | --- | --- | --- |
| GET | `/api/v1/organizations` | authenticated | Organizations the caller belongs to, with the caller's role. |
| POST | `/api/v1/organizations` | authenticated | Create an organization. The caller becomes `OWNER`. |
| GET | `/api/v1/organizations/{orgId}` | `ORG_READ` | Organization details. |
| PATCH | `/api/v1/organizations/{orgId}` | `ORG_UPDATE` | Update the name. |
| DELETE | `/api/v1/organizations/{orgId}` | `ORG_DELETE` | Delete the organization. |
| GET | `/api/v1/organizations/{orgId}/members` | `MEMBER_READ` | List members. |
| POST | `/api/v1/organizations/{orgId}/members` | `MEMBER_MANAGE` | Add an existing user `{ "username": "octocat", "role": "DEVELOPER" }`. |
| PATCH | `/api/v1/organizations/{orgId}/members/{userId}` | `MEMBER_MANAGE` | Change role `{ "role": "ADMIN" }`. |
| DELETE | `/api/v1/organizations/{orgId}/members/{userId}` | `MEMBER_MANAGE` (or self) | Remove a member, or leave the organization. |

`POST /organizations` request `{ "name": "Acme Platform", "slug": "acme-platform" }`. If `slug` is
omitted, it is derived from `name` (pattern `^[a-z0-9]+(-[a-z0-9]+)*$`, 3–50 characters). The slug
cannot be changed later.

Organization response. `role` and `permissions` describe the **caller's** access, so clients can
adapt the UI without duplicating the RBAC matrix. The server still enforces every check.

```json
{
  "id": "4c1f…",
  "name": "Acme Platform",
  "slug": "acme-platform",
  "role": "ADMIN",
  "permissions": ["ORG_READ", "ORG_UPDATE", "MEMBER_READ", "MEMBER_MANAGE", "PROJECT_READ", "…"],
  "createdAt": "2026-09-25T10:15:30Z"
}
```

Member response: `{ "userId", "username", "displayName", "avatarUrl", "role", "joinedAt" }`.

Membership rules: Owners manage everyone. Admins manage only Developers and Viewers and cannot grant
Owner (`403`). Any member can leave. Removing or demoting the last Owner returns `409`.

## GitHub and projects — Phase 3

| Method | Path | Permission | Description |
| --- | --- | --- | --- |
| GET | `/api/v1/github/repositories?page&perPage` | authenticated | Repositories the caller can access on GitHub. |
| GET | `/api/v1/github/repositories/{owner}/{repo}/branches` | authenticated | Branches. |
| GET | `/api/v1/github/repositories/{owner}/{repo}/commits?branch` | authenticated | Recent commits. |
| GET | `/api/v1/github/repositories/{owner}/{repo}/pulls?state` | authenticated | Pull requests. |
| GET | `/api/v1/organizations/{orgId}/projects` | `PROJECT_READ` | List projects. |
| POST | `/api/v1/organizations/{orgId}/projects` | `PROJECT_WRITE` | Create a project from a repository `{ "name", "slug?", "description?", "repositoryFullName": "owner/repo" }`. |
| GET | `/api/v1/projects/{projectId}` | `PROJECT_READ` | Project with repository, environments summary, latest deployments. |
| PATCH | `/api/v1/projects/{projectId}` | `PROJECT_WRITE` | Update name or description. |
| DELETE | `/api/v1/projects/{projectId}` | `PROJECT_DELETE` | Delete a project. |
| POST | `/api/v1/projects/{projectId}/sync` | `PROJECT_WRITE` | Re-sync repository and branch metadata from GitHub. |
| GET | `/api/v1/projects/{projectId}/branches` | `PROJECT_READ` | Stored branches. |
| GET | `/api/v1/projects/{projectId}/commits?branch` | `PROJECT_READ` | Recent commits (live from GitHub). |
| GET | `/api/v1/projects/{projectId}/pulls` | `PROJECT_READ` | Pull requests (live from GitHub). |

For project-scoped endpoints the backend resolves the project's organization and checks the caller's
permission in that organization. Non-members get `404` for every project endpoint.

All GitHub calls use the **caller's** GitHub token, so GitHub's own permissions apply as well. A
repository the caller cannot see on GitHub returns `404`. A GitHub failure (revoked token, missing
scope, rate limit, outage) returns `502` with type `urn:cloudflow:problem:upstream-error` and
`"service": "GitHub"`.

`GET /github/repositories` →
`{ "items": [GithubRepository], "page": 1, "perPage": 30, "hasNext": true }`, where each item is
`{ id, owner, name, fullName, description, htmlUrl, defaultBranch, isPrivate, language, pushedAt }`.

`POST /organizations/{orgId}/projects` → `201`. The project name defaults to the repository name
and the slug is derived from the name. The backend fetches repository metadata and branches (up to
300) and detects the application type from root files. Priority: `Dockerfile` → `DOCKERFILE`,
`pom.xml` → `JAVA_MAVEN`, `build.gradle(.kts)` → `JAVA_GRADLE`, `package.json` → `NODE`,
`requirements.txt`/`pyproject.toml` → `PYTHON`, otherwise `UNKNOWN`. Linking a repository that
already belongs to a project in the same organization returns `409`.

Project response:

```json
{
  "id": "…", "organizationId": "…", "name": "payments-api", "slug": "payments-api",
  "description": "Payments REST API", "appType": "JAVA_MAVEN",
  "repository": {
    "githubRepoId": 1001, "owner": "acme", "name": "payments-api", "fullName": "acme/payments-api",
    "htmlUrl": "https://github.com/acme/payments-api", "defaultBranch": "main", "isPrivate": true,
    "language": "Java", "lastSyncedAt": "2026-09-25T10:00:00Z"
  },
  "role": "DEVELOPER", "permissions": ["PROJECT_READ", "PROJECT_WRITE", "…"],
  "createdAt": "…", "updatedAt": "…"
}
```

The list endpoint returns summaries (no `role`/`permissions`). Branch:
`{ name, headCommitSha, isProtected }`. Commit (subject line only):
`{ sha, shortSha, message, authorName, authorLogin, authorAvatarUrl, committedAt, htmlUrl }`. Pull
request: `{ number, title, state, draft, authorLogin, authorAvatarUrl, headBranch, baseBranch,
htmlUrl, createdAt, updatedAt }`.

## Environments and configuration — Phase 4

| Method | Path | Permission | Description |
| --- | --- | --- | --- |
| GET | `/api/v1/projects/{projectId}/environments` | `ENV_READ` | List environments. |
| POST | `/api/v1/projects/{projectId}/environments` | `ENV_WRITE`\* | Create `{ "type": "STAGING", "branch": "main" }`. |
| GET / PATCH / DELETE | `/api/v1/environments/{envId}` | `ENV_READ` / `ENV_WRITE`\* | Environment details / update / delete. |
| GET | `/api/v1/environments/{envId}/variables` | `ENV_READ` | Variables. Secret values are returned as `null` with `"secret": true`. |
| PUT | `/api/v1/environments/{envId}/variables/{key}` | `ENV_WRITE`\* | Create or replace `{ "value": "…", "secret": true }`. |
| DELETE | `/api/v1/environments/{envId}/variables/{key}` | `ENV_WRITE`\* | Delete a variable. |
| GET / PUT | `/api/v1/environments/{envId}/config` | `ENV_READ` / `ENV_WRITE`\* | Deployment configuration. |
| POST | `/api/v1/environments/{envId}/config/validate` | `ENV_READ` | Validate without deploying → `{ "valid": false, "errors": [...] }`. |
| GET | `/api/v1/config-templates` | authenticated | Available templates (`JAVA`, `NODE`, `PYTHON`, `DOCKER`) with defaults. |
| GET | `/api/v1/deployment-targets` | authenticated | `[{ target, label, available }]` for `DOCKER` and `KUBERNETES` (Phase 10). |

\* `ENV_WRITE_PRODUCTION` for Production environments.

Environment response:
`{ id, projectId, type, branch, config: DeploymentConfig, variableCount, secretCount, createdAt, updatedAt }`.
A new environment deploys the repository's default branch unless `branch` is given (it must exist
in the synced branches, otherwise `400`). It starts with the template recommended for the project's
detected application type. Each project has at most one environment per type (`409`).

Variables: `PUT` returns `201` when it creates a variable and `200` when it replaces one. Keys must
match `^[A-Z_][A-Z0-9_]*$` (at most 128 characters), and the `CLOUDFLOW_` prefix is reserved
(`400`). Values are at most 32 KB. Secret values are encrypted with AES-256-GCM before they are
stored and are **write-only**: every read returns `"value": null`.

Deployment configuration (`GET`/`PUT /environments/{envId}/config`):

```json
{
  "template": "JAVA", "runtimeVersion": "21",
  "buildCommand": "mvn -B -DskipTests package", "startCommand": "java -jar app.jar",
  "dockerfilePath": "Dockerfile", "containerPort": 8080, "healthCheckPath": "/",
  "cpuLimit": 1.00, "memoryLimitMb": 512, "target": "DOCKER", "updatedAt": "…"
}
```

`PUT` checks the request shape (`400`). It accepts semantically incomplete configurations so work
in progress can be saved. The **validator** decides whether a configuration can be deployed:
`POST …/config/validate` → `{ "valid": false, "errors": [{field, message}], "warnings": [...] }`.
Errors block deployment. They cover: unknown branch; missing start command or runtime version
(non-Docker templates); Dockerfile path outside the repository; multi-line commands; invalid
health-check path; CPU outside 0.1–8; memory outside 128–16384 MB; a deployment `target` this
installation is not configured for. Warnings cover: template does not
match the detected application type; port below 1024; missing resource limits.

`GET /config-templates?appType=JAVA_GRADLE` → `[{ template, label, description, recommended, defaults }]`.
Defaults: Java 21 (`mvn -B -DskipTests package` or `gradle build -x test --no-daemon`,
`java -jar app.jar`, port 8080), Node.js 22 (`npm ci && npm run build --if-present`, `npm start`,
port 3000), Python 3.12 (`pip install --no-cache-dir -r requirements.txt`, `python app.py`, port
8000), Dockerfile (`Dockerfile`, port 8080).

## Deployments — Phase 5

| Method | Path | Permission | Description |
| --- | --- | --- | --- |
| POST | `/api/v1/environments/{envId}/deployments` | `DEPLOYMENT_TRIGGER`\* | Start a deployment of the environment's branch head, or `{ "commitSha": "…" }` → `202` + `Location`. |
| GET | `/api/v1/environments/{envId}/deployments?page&size` | `DEPLOYMENT_READ` | Environment deployment history, newest first. |
| GET | `/api/v1/projects/{projectId}/deployments?page&size` | `DEPLOYMENT_READ` | Project deployment history, newest first. |
| GET | `/api/v1/deployments/{deploymentId}` | `DEPLOYMENT_READ` | Deployment details and status. |
| GET | `/api/v1/deployments/{deploymentId}/logs?afterId=0&limit=500` | `DEPLOYMENT_READ` | Log lines after `afterId`, oldest first (maximum 1000 per call). Clients poll with the last id they saw. |
| POST | `/api/v1/deployments/{deploymentId}/rollback` | `DEPLOYMENT_TRIGGER`\* | Redeploy this deployment's image → `202`. |
| POST | `/api/v1/deployments/{deploymentId}/cancel` | `DEPLOYMENT_TRIGGER`\* | Cancel a queued or building deployment. |

\* `DEPLOYMENT_TRIGGER_PRODUCTION` for Production environments.

Rules:

- `422` (`urn:cloudflow:problem:unprocessable`) with `errors[]` when the environment's configuration
  fails validation. The validator runs again when the deployment starts.
- `409` when another deployment of the environment is already in progress. A partial unique index
  enforces this in the database as well.
- Rollback: only to a `SUCCEEDED` or `ROLLED_BACK` deployment that has an image and is not the live
  one (`409` otherwise). The image is reused, not rebuilt. When the rollback succeeds, the
  deployment it replaced becomes `ROLLED_BACK`.
- Cancel: only while `QUEUED` or `BUILDING` (`409` otherwise). The runner stops at the next step.

Deployment response:

```json
{
  "id": "…", "projectId": "…", "environmentId": "…",
  "status": "SUCCEEDED", "triggerType": "MANUAL", "triggeredBy": "…",
  "branch": "main", "commitSha": "1111111…", "commitMessage": "Add greeting endpoint",
  "appType": "PYTHON",
  "imageTag": "localhost:5000/acme/hello-python:development-1111111-e64a226f",
  "imageId": "sha256:…", "containerName": "cf-hello-python-development-e64a226f",
  "hostPort": 55709, "url": "http://localhost:55709",
  "rollbackOfId": null, "active": true, "failureReason": null,
  "createdAt": "…", "startedAt": "…", "finishedAt": "…"
}
```

`url` is set only for the live (`active`) deployment. Log line:
`{ id, phase: SOURCE|BUILD|PUSH|DEPLOY|HEALTH_CHECK|RUNTIME, level: INFO|WARN|ERROR, message, loggedAt }`.
Secret variable values are masked (`********`) before a log line is stored.

### Deployment targets (Phase 10)

`target` in the deployment configuration is `DOCKER` (default) or `KUBERNETES`. Omitting it in a
`PUT` means `DOCKER`. Deployment responses include the `target` they ran on; for Kubernetes,
`containerName` is the workload name, `hostPort` the NodePort, and `url` uses the Kubernetes public
host.

## CI/CD — Phase 6

| Method | Path | Permission | Description |
| --- | --- | --- | --- |
| GET | `/api/v1/projects/{projectId}/pipelines` | `PIPELINE_READ` | The project's pipelines (one per environment), each with its latest run. |
| POST | `/api/v1/environments/{envId}/pipeline/preview` | `PIPELINE_WRITE` | Render the workflow for approval: `{ workflowPath, branch, content, fileExists, secrets[] }`. Nothing is written. |
| POST | `/api/v1/environments/{envId}/pipeline` | `PIPELINE_WRITE` | Commit the approved workflow to the environment's branch (creates or replaces it) → `201`. |
| GET | `/api/v1/pipelines/{pipelineId}` | `PIPELINE_READ` | Pipeline with its latest run. |
| GET | `/api/v1/pipelines/{pipelineId}/runs?page&size` | `PIPELINE_READ` | Build history, newest first, each run with its jobs. |
| POST | `/api/v1/pipelines/{pipelineId}/sync` | `PIPELINE_READ` | Pull recent runs and jobs from the GitHub Actions API. |
| DELETE | `/api/v1/pipelines/{pipelineId}` | `PIPELINE_WRITE` | Stop tracking. The workflow file stays in the repository. |
| GET | `/api/v1/environments/{envId}/deploy-tokens` | `PIPELINE_READ` | Deploy tokens (name, prefix, created, last used, revoked). |
| POST | `/api/v1/environments/{envId}/deploy-tokens` | `DEPLOYMENT_TRIGGER`\* | Create `{ "name": "…" }` → `201 { deployToken, token, secretName }`. The raw token is returned **only once** (`Cache-Control: no-store`). |
| DELETE | `/api/v1/deploy-tokens/{tokenId}` | `DEPLOYMENT_TRIGGER`\* | Revoke a token. |
| POST | `/api/v1/pipeline-hooks/deploy` | deploy token | Deploy stage called from GitHub Actions: `{ "commitSha": "<40 hex>", "runId": 123 }` → `202 { id, status, commitSha, url, failureReason }`. |
| GET | `/api/v1/pipeline-hooks/deployments/{id}` | deploy token | Status polled by the health-check stage. Only deployments of the token's environment are visible. |
| POST | `/api/v1/webhooks/github` | HMAC signature | `workflow_run` / `workflow_job` events → `204` (tracked run updated) or `202` (ignored). `404` when no webhook secret is configured, `401` for a bad signature. |

\* `DEPLOYMENT_TRIGGER_PRODUCTION` for Production.

**Deploy tokens** (`cfd_` + 40 random characters) are sent in the `X-CloudFlow-Deploy-Token` header
and stored only as SHA-256 hashes. A token acts **as the user who created it**, and that user's
permission to deploy the environment is re-checked on every call. Removing the user from the
organization, or lowering their role, disables their tokens immediately. Unknown or revoked tokens
get `401`.

**Generated workflow** (`.github/workflows/cloudflow-<env>.yml`, triggered on pushes to the
environment's branch and on `workflow_dispatch`):

| Job | What it does |
| --- | --- |
| `test` | Stack toolchain (`setup-java` / `setup-node` / `setup-python`) plus tests (`mvn -B test`, `gradle test`, `npm test --if-present`, `pytest`). The Docker template detects the runner from the files present. |
| `build` | The environment's build command (Docker template: `docker build`). |
| `docker` | Builds with the same Dockerfile CloudFlow uses (the generated one is written inline for template builds) and pushes `ghcr.io/<owner>/<repo>:<sha>` and `:<env>` using `GITHUB_TOKEN`. |
| `deploy` | `POST /pipeline-hooks/deploy` with the commit SHA and run id. Outputs the deployment id. |
| `health-check` | Polls the deployment until `SUCCEEDED` (passes) or `FAILED`/`CANCELLED` (fails the workflow, printing the failure reason), timing out after 20 minutes. |

Required repository secrets: `CLOUDFLOW_URL` (the public API URL) and
`CLOUDFLOW_DEPLOY_TOKEN_<ENVIRONMENT>`. `${{ … }}` sequences in user-provided commands are escaped
so they cannot inject GitHub expressions. Generated workflows are checked with actionlint (including
shellcheck).

## Monitoring and logs — Phase 7

| Method | Path | Permission | Description |
| --- | --- | --- | --- |
| GET | `/api/v1/environments/{envId}/metrics` | `DEPLOYMENT_READ` | Service status (`NOT_DEPLOYED`, `UNKNOWN`, `UP`, `DEGRADED`, `DOWN`), latest sample, 1-hour health summary, recent samples. |
| GET | `/api/v1/environments/{envId}/events?limit=50` | `DEPLOYMENT_READ` | Recent events, newest first. |
| GET | `/api/v1/environments/{envId}/runtime-logs?tail=200` | `DEPLOYMENT_READ` | Last lines of the running container's output (secrets masked). |
| GET | `/actuator/prometheus` (management port 8081) | internal network only | Prometheus scrape endpoint. Denied on the API port. |
| WS | `/ws` (STOMP) | JWT in the CONNECT frame | Topics below. Each SUBSCRIBE is authorized; unknown or forbidden topics close the session. |

WebSocket topics:

| Topic | Payload |
| --- | --- |
| `/topic/deployments/{id}/logs` | Deployment log line as it is written (same shape as the REST log line) |
| `/topic/deployments/{id}/status` | `{ deploymentId, status, failureReason }` on every status change |
| `/topic/environments/{id}/logs` | `{ deploymentId, message, receivedAt }`: live container output. The container is followed only while someone is subscribed. |
| `/topic/environments/{id}/metrics` | Each new metric sample (every 15 seconds) |

Metric sample: `{ timestamp, deploymentId, running, healthy, cpuPercent, memoryBytes,
memoryLimitBytes, networkRxBytes, networkTxBytes, uptimeSeconds, restartCount, responseTimeMs,
statusCode }`. Health summary: `{ window, checks, uptimePercent, errorRatePercent,
averageResponseTimeMs, p95ResponseTimeMs }`. The error rate is the share of probes that got no
response or a non-2xx/3xx status.

Event types: `DEPLOYMENT_STARTED`, `DEPLOYMENT_SUCCEEDED`, `DEPLOYMENT_FAILED`,
`DEPLOYMENT_CANCELLED`, `DEPLOYMENT_ROLLED_BACK`, `HEALTH_DEGRADED`, `HEALTH_RECOVERED`,
`CONTAINER_RESTARTED`, `CONTAINER_STOPPED`. Severity is `INFO`, `WARN`, or `ERROR`.

Prometheus metrics, all tagged `project`, `environment`, and `environment_id`:
`cloudflow_app_up`, `cloudflow_app_cpu_usage_percent`, `cloudflow_app_memory_usage_bytes`,
`cloudflow_app_memory_limit_bytes`, `cloudflow_app_network_received_bytes`,
`cloudflow_app_network_transmitted_bytes`, `cloudflow_app_uptime_seconds`,
`cloudflow_app_restarts`, `cloudflow_app_response_time_milliseconds`, and
`cloudflow_app_health_checks_total{result}`.

## AI assistant — Phase 8 (backend façade)

| Method | Path | Permission | Description |
| --- | --- | --- | --- |
| POST | `/api/v1/projects/{projectId}/assistant/query` | `AI_USE` | RAG question `{ "question": "…", "environmentId?": "…" }`, answered with the project's knowledge plus live state (environments, recent deployments, service status, events). |
| POST | `/api/v1/deployments/{deploymentId}/analysis` | `DEPLOYMENT_READ` + `AI_USE` | Explains a deployment (usually a failure) from its evidence. |
| GET | `/api/v1/projects/{projectId}/knowledge` | `PROJECT_READ` | `{ enabled, documents, chunks, lastIndexedAt }` |
| POST | `/api/v1/projects/{projectId}/knowledge/reindex` | `AI_USE` | Full re-index → `{ collected, indexed, unchanged, removed }` |
| GET | `/api/v1/projects/{projectId}/suggestions` | `PROJECT_READ` | The 50 latest suggestions. |
| POST | `/api/v1/projects/{projectId}/suggestions` | `AI_USE` | Generate `{ "type": "DOCKERFILE" \| "ENV_TEMPLATE" \| "WORKFLOW" \| "DOCUMENTATION", "environmentId?", "instructions?" }` → `201`, status `PENDING`. |
| POST | `/api/v1/suggestions/{id}/apply` | `AI_APPLY` + the change's own permission | Files: commit through the GitHub Contents API (`PROJECT_WRITE`; workflows also need `PIPELINE_WRITE`). Env templates: create the missing variables (`ENV_WRITE` or `ENV_WRITE_PRODUCTION`). → `APPLIED` with a `result` summary. |
| POST | `/api/v1/suggestions/{id}/reject` | `AI_USE` | → `REJECTED`. Only `PENDING` suggestions can be applied or rejected (`409` otherwise). |

AI answer shape:

```json
{
  "answer": "…",
  "likelyCause": "…",
  "evidence": [{ "source": "deployment-logs", "reference": "S2 · deployment:9f…", "excerpt": "…" }],
  "recommendedActions": ["…"],
  "confidence": "HIGH | MEDIUM | LOW",
  "confidenceExplanation": "…"
}
```

**What is sent to the AI.** Knowledge sources: repository README, Dockerfiles, compose files,
build manifests, application configuration, workflows, and `docs/` (default branch, at most 40
files of 100 KB each); every environment's configuration and variables; the 20 most recent finished
deployments; the last 80 log lines of failed deployments; recent events. Secret variable values are
**never** sent, only `<secret, value hidden>`, and log lines were masked when stored. Finished
deployments are indexed automatically in the background.

**Safety.** Generated files are written only when a user applies them. Target paths are enforced
by the server (the model's proposed path is used only if it matches the type's safe pattern), and
CloudFlow's own `cloudflow-*.yml` pipelines can never be overwritten. Environment templates never
create placeholder secrets; the result lists the secrets the user must set. When the AI service is
unavailable or not configured, AI endpoints answer `502` (`"service": "AI service"`), and every other
feature keeps working.

## Internal AI-service API (backend → ai-service only)

Authenticated with `X-Internal-Token` (the `CLOUDFLOW_AI_INTERNAL_TOKEN` shared secret). Reachable
only on the platform network.

| Method | Path | Description |
| --- | --- | --- |
| GET | `/health` | `{ status, aiConfigured }` |
| POST | `/v1/index/documents` | `{ projectId, documents: [{ sourceType, sourceRef, environmentId?, deploymentId?, content, metadata }], replaceAll }`. Unchanged documents (same SHA-256) are skipped. |
| GET / DELETE | `/v1/index/projects/{projectId}` | Knowledge statistics / remove all of a project's knowledge. |
| POST | `/v1/assistant/query` | Retrieval (cosine similarity via pgvector HNSW, filtered by project and environment) + structured answer. |
| POST | `/v1/analysis/deployment` | Deployment analysis: retrieval driven by the failure reason and the last log lines. |
| POST | `/v1/generate/{type}` | `{ filePath, content, explanation }` |

`503` means the model provider is not configured or failed.

## Audit — Phase 9

| Method | Path | Permission | Description |
| --- | --- | --- | --- |
| GET | `/api/v1/organizations/{orgId}/audit-logs?action&actorId&from&to&page&size` | `AUDIT_READ` | Paginated audit trail, newest first (`size` ≤ 200, default 50). `from`/`to` are ISO-8601 instants. |

Entry: `{ id, action, actorId, actorUsername, resourceType, resourceId, details, ipAddress, createdAt }`.
`details` holds non-secret context (names, roles, variable keys, never values).

Recorded actions: `USER_SIGNED_IN`, `USER_SIGNED_OUT`, `REFRESH_TOKEN_REUSE_DETECTED`,
`ORGANIZATION_CREATED|UPDATED|DELETED`, `MEMBER_ADDED|ROLE_CHANGED|REMOVED`,
`PROJECT_CREATED|UPDATED|DELETED`, `ENVIRONMENT_CREATED|UPDATED|DELETED`, `VARIABLE_SET|DELETED`,
`DEPLOYMENT_CONFIG_UPDATED`, `DEPLOYMENT_TRIGGERED`, `DEPLOYMENT_ROLLBACK_TRIGGERED`,
`DEPLOYMENT_CANCELLED`, `PIPELINE_COMMITTED|DELETED`, `DEPLOY_TOKEN_CREATED|REVOKED`,
`AI_SUGGESTION_APPLIED|REJECTED`. Sign-in events are account-level (no organization) and are kept but
not shown in organization views.

### Image scan fields on deployments

Deployment responses include `scanStatus` (`PASSED` | `VULNERABLE` | `BLOCKED` | `ERROR` |
`SKIPPED`, `null` for rollbacks of pre-Phase-9 images), `vulnerabilitiesCritical`, and
`vulnerabilitiesHigh`. Scan output appears in the deployment log under phase `SCAN`.

## Artifacts — Phase 10

| Method | Path | Permission | Description |
| --- | --- | --- | --- |
| GET | `/api/v1/projects/{projectId}/artifacts?kind&deploymentId&page&size` | `DEPLOYMENT_READ` | Stored artifacts, newest first (`size` ≤ 100, default 50). |
| GET | `/api/v1/artifacts/{artifactId}/content` | `DEPLOYMENT_READ` | Download: `application/octet-stream`, `Content-Disposition: attachment`. `404` for artifacts outside the caller's organizations; `502` (`upstream-error`) if storage is unreachable or disabled. |

Artifact: `{ id, projectId, environmentId, deploymentId, kind, name, contentType, sizeBytes, sha256,
createdBy, createdAt }`. `kind` is `BUILD_ARTIFACT` (Dockerfile, `vulnerability-report.json`),
`GENERATED_FILE` (committed workflow or AI-generated file; `name` is the repository path), or
`ARCHIVED_LOG` (`deployment-<id>.log`). When artifact storage is disabled nothing is stored and the
list is empty.

## Operational endpoints

| Method | Path | Auth | Description |
| --- | --- | --- | --- |
| GET | `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness` | management port 8081 (internal) | Backend health probes. Since Phase 7, Actuator runs on the internal management port. |
| GET | `/health` (ai-service) | internal | AI service liveness (available since Phase 1). |
