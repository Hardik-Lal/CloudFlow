# CloudFlow — Deployment

This document covers how CloudFlow itself is run, its deployment and trust boundaries, and its
configuration. How CloudFlow deploys *user applications* is described in
[architecture.md](architecture.md) §7.2.

## 1. Deployment boundaries

```mermaid
flowchart TB
    subgraph public[Public zone — reachable from browsers]
        fe[frontend :3000]
        be[backend :8080<br/>/api/v1, /ws, /oauth2, /login]
    end
    subgraph internal[Internal zone — platform network only]
        ai[ai-service :8000]
        pg[(postgres :5432)]
        prom[prometheus :9090]
    end
    subgraph privileged[Privileged zone]
        sock[/var/run/docker.sock/]
    end
    subgraph apps[Application zone — network cloudflow-apps]
        a1[deployed app containers]
        k8s[Kubernetes cluster<br/>optional target]
    end
    s3[(S3 bucket<br/>artifacts)]

    fe -. browser calls .-> be
    be --> ai
    be --> pg
    ai --> pg
    be --> sock
    sock --> a1
    be -->|Kubernetes API| k8s
    be -->|S3 API| s3
    prom --> be
```

| Boundary | Rule |
| --- | --- |
| Public → backend | Only the backend API, OAuth endpoints, and WebSocket are exposed. JWT is required except on public endpoints. CORS allows only `FRONTEND_URL`. |
| Backend → AI service | Internal network only. Requests carry `X-Internal-Token`. Secrets are removed from any context sent. |
| Backend → Docker socket | Only the backend mounts the Docker socket, and it still runs as a non-root user (socket access through the supplementary group `DOCKER_SOCKET_GID`). Access to the socket is equivalent to root on the host, so the backend is the most privileged component. User input reaches Docker only after validation: image names come from slugs, ports and limits are range-checked, env keys match `^[A-Z_][A-Z0-9_]*$`, commands are single-line, Dockerfile paths stay inside the repository. |
| Deployed applications | Run on a separate `cloudflow-apps` network, with no route to the platform network, the database, or the AI service. Non-root users and resource limits are applied (Phase 9). |
| Database | Never published outside the platform network in production. The backend and AI service use their own schemas. |
| Backend → Kubernetes | Credentials from a kubeconfig (or the in-cluster service account). CloudFlow only creates Deployments, Services, and PersistentVolumeClaims in its namespace (default `cloudflow-apps`) and reads pod logs and kubelet stats; see "Kubernetes target" for the minimum RBAC. Pods get no service-account token. |
| Backend → object storage | One bucket, private. Only the backend holds credentials (an IAM role on AWS). Browsers never get bucket URLs: downloads are streamed through the API after an RBAC check. |
| External services | GitHub (OAuth, API, webhooks verified with HMAC), OpenAI (called only from the AI service), Amazon S3 (Phase 10). |

## 2. Local development

### Prerequisites

| Tool | Version |
| --- | --- |
| Docker + Docker Compose | Docker 24+ |
| JDK | 21 (Gradle provisions it automatically if it is missing) |
| Node.js | 20.9+ (22 LTS recommended) |
| Python | 3.12 via [uv](https://docs.astral.sh/uv/) |

### GitHub OAuth App (one-time)

Create an OAuth App at <https://github.com/settings/developers> → **OAuth Apps** → **New OAuth App**:

| Field | Value |
| --- | --- |
| Homepage URL | `http://localhost:3000` |
| Authorization callback URL | `http://localhost:8080/login/oauth2/code/github` (use the backend's public URL) |

Put the client ID and a generated client secret into `infrastructure/.env` (or the backend's
environment when running from source).

### Full stack in Docker

```bash
cd infrastructure
cp .env.example .env          # set POSTGRES_PASSWORD, GitHub OAuth values, S3 keys, and generate the secrets
docker compose up --build -d
```

If port 8080 is taken, set `BACKEND_PORT` and `PUBLIC_API_BASE_URL` to another port (e.g. 8081) and
use that port in the OAuth App's callback URL.

| Service | URL |
| --- | --- |
| Frontend | http://localhost:3000 |
| Image registry | http://127.0.0.1:5000/v2/_catalog |
| Prometheus | http://127.0.0.1:9090 |
| Grafana (optional, `--profile grafana`) | http://127.0.0.1:3001, dashboard "CloudFlow · Applications" |
| Kubernetes (optional, `--profile kubernetes`) | apps on `http://localhost:30000-30049` |
| Object storage | internal only (`http://object-storage:8333`, S3 API) |
| Deployed applications | `http://localhost:<published port>` (shown on each deployment) |
| Backend health | internal only: `docker compose exec backend wget -qO- localhost:8081/actuator/health` |
| AI service | internal only (`http://ai-service:8000` on the platform network) |
| PostgreSQL | localhost:5432 |

### Running services from source

```bash
# database only
cd infrastructure && docker compose up -d postgres

# backend  (http://localhost:8080) — export GITHUB_CLIENT_ID, GITHUB_CLIENT_SECRET,
# CLOUDFLOW_JWT_SECRET and CLOUDFLOW_ENCRYPTION_KEY first. Outside Docker the backend cannot
# resolve container names, so probe deployed apps through their published ports:
export CLOUDFLOW_HEALTH_CHECK_HOST=localhost
cd backend && ./gradlew bootRun

# frontend (http://localhost:3000)
cd frontend && npm install && npm run dev

# ai-service (http://localhost:8000)
cd ai-service && uv sync && uv run uvicorn app.main:app --reload --port 8000
```

## 3. Configuration reference

All configuration comes from environment variables. Real values are never committed: each component
has an `.env.example` file.

### infrastructure (`infrastructure/.env`)

| Variable | Default | Description |
| --- | --- | --- |
| `CLOUDFLOW_ENVIRONMENT` | `local` | Deployment environment name |
| `POSTGRES_DB` / `POSTGRES_USER` | `cloudflow` | Database name / user |
| `POSTGRES_PASSWORD` | — (required) | Database password |
| `POSTGRES_PORT` | `5432` | Host port for PostgreSQL |
| `BACKEND_PORT` / `FRONTEND_PORT` | `8080` / `3000` | Host ports |
| `PUBLIC_API_BASE_URL` | `http://localhost:8080` | Backend URL as seen by browsers (built into the frontend) |
| `FRONTEND_URL` | `http://localhost:3000` | Frontend URL: post-login redirect target and allowed CORS origin |
| `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET` | — (required) | GitHub OAuth App credentials |
| `CLOUDFLOW_JWT_SECRET` | — (required) | Access-token signing secret, at least 32 characters |
| `CLOUDFLOW_ENCRYPTION_KEY` | — (required) | Base64 AES-256 key (32 bytes) for secrets at rest |
| `CLOUDFLOW_COOKIE_SECURE` | `false` | Set to `true` when served over HTTPS |
| `REGISTRY_PORT` | `5000` | Host port of the bundled image registry (bound to 127.0.0.1) |
| `DOCKER_SOCKET_GID` | `0` | Group id owning `/var/run/docker.sock` inside containers: `0` on Docker Desktop; on Linux `getent group docker \| cut -d: -f3` |
| `CLOUDFLOW_PUBLIC_HOST` | `localhost` | Host name users open deployed applications with |
| `CLOUDFLOW_GITHUB_WEBHOOK_SECRET` | empty | Optional GitHub webhook secret (see `.env.example`) |
| `CLOUDFLOW_AI_INTERNAL_TOKEN` | — (required) | Shared secret between the backend and the AI service (`openssl rand -hex 32`) |
| `OPENAI_API_KEY` | empty | OpenAI API key. Empty disables AI features only. |
| `OPENAI_CHAT_MODEL` / `OPENAI_EMBEDDING_MODEL` | `gpt-5.4-mini` / `text-embedding-3-small` | Models used by the AI service (embeddings must be 1536-dimensional) |
| `PROMETHEUS_PORT` / `GRAFANA_PORT` | `9090` / `3001` | Monitoring ports (bound to 127.0.0.1) |
| `GRAFANA_ADMIN_PASSWORD` | `change-me` | Grafana admin password |
| `CLOUDFLOW_SCAN_ENABLED` / `CLOUDFLOW_SCAN_BLOCK_ON_CRITICAL` | `true` / `false` | Image scanning (Phase 9) |
| `CLOUDFLOW_RATE_LIMIT_ENABLED` | `true` | API rate limits (Phase 9) |
| `CLOUDFLOW_S3_ACCESS_KEY` / `CLOUDFLOW_S3_SECRET_KEY` | — (required for the bundled store) | Keys of the local S3-compatible store; the backend uses the same keys. Empty in AWS (instance role). |
| `CLOUDFLOW_S3_BUCKET` / `CLOUDFLOW_S3_REGION` | `cloudflow-artifacts` / `us-east-1` | Artifact bucket |
| `CLOUDFLOW_S3_ENDPOINT` / `CLOUDFLOW_S3_PATH_STYLE` | `http://object-storage:8333` / `true` | S3 endpoint; empty endpoint and `false` for Amazon S3 |
| `CLOUDFLOW_STORAGE_ENABLED` | `true` | Store artifacts |
| `CLOUDFLOW_KUBERNETES_ENABLED` | `false` | Offer the Kubernetes target (start the `kubernetes` profile first) |
| `CLOUDFLOW_DATABASE_URL`, `CLOUDFLOW_DATABASE_USERNAME`, `CLOUDFLOW_DATABASE_PASSWORD`, `CLOUDFLOW_AI_DATABASE_URL` | — | Production override only: managed database (see §7) |

### backend

| Variable | Default | Description |
| --- | --- | --- |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/cloudflow` | JDBC URL |
| `MANAGEMENT_PORT` | `8081` | Internal Actuator port (health, Prometheus). Never publish it. |
| `CLOUDFLOW_MONITORING_INTERVAL` | `15s` | Sampling interval for metrics and health probes |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | `cloudflow` / `cloudflow` | Credentials |
| `SERVER_PORT` | `8080` | HTTP port |
| `CLOUDFLOW_LOG_LEVEL` | `INFO` | Log level for `com.cloudflow` |
| `FRONTEND_URL` | `http://localhost:3000` | Redirect target after GitHub sign-in |
| `CORS_ALLOWED_ORIGINS` | `FRONTEND_URL` | Comma-separated browser origins allowed to call the API |
| `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET` | — (required) | GitHub OAuth App. Scopes requested: `read:user user:email repo workflow` |
| `CLOUDFLOW_JWT_SECRET` | — (required) | HS256 signing secret, at least 32 characters |
| `CLOUDFLOW_ENCRYPTION_KEY` | — (required) | Base64-encoded 32-byte AES key (`openssl rand -base64 32`) |
| `CLOUDFLOW_COOKIE_SECURE` | `false` | `Secure` flag on the refresh-token cookie |
| `CLOUDFLOW_GITHUB_APIBASEURL` | `https://api.github.com` | GitHub REST API base URL (for GitHub Enterprise) |
| `CLOUDFLOW_GITHUB_TIMEOUT` | `10s` | Connect and read timeout for GitHub calls |
| `DOCKER_HOST` | `unix:///var/run/docker.sock` | Docker Engine used by the deployment engine |
| `CLOUDFLOW_REGISTRY` | `localhost:5000` | Registry that images are tagged for and pushed to (resolved by the Docker daemon) |
| `CLOUDFLOW_WORK_DIRECTORY` | `${java.io.tmpdir}/cloudflow-builds` | Scratch space for source checkouts |
| `CLOUDFLOW_HEALTH_CHECK_HOST` | empty | Empty: probe containers by name on `cloudflow-apps` (backend in Docker). Set to `localhost` when the backend runs on the host. |
| `CLOUDFLOW_PUBLIC_HOST` | `localhost` | Host name in the URLs of deployed applications |
| `CLOUDFLOW_PUBLIC_API_URL` | `http://localhost:8080` | API URL reachable from GitHub Actions runners (value of the `CLOUDFLOW_URL` secret) |
| `CLOUDFLOW_GITHUB_WEBHOOK_SECRET` | empty | Enables `/api/v1/webhooks/github` for live pipeline updates |
| `CLOUDFLOW_DEPLOYMENT_CONCURRENCY`, `..._QUEUECAPACITY`, `..._HEALTHCHECKTIMEOUT`, `..._BUILDTIMEOUT` | `2`, `50`, `90s`, `20m` | Engine tuning (`cloudflow.deployment.*`) |
| `CLOUDFLOW_SECURITY_SCAN_ENABLED` | `true` | Scan each built image with Trivy (Compose: `CLOUDFLOW_SCAN_ENABLED`) |
| `CLOUDFLOW_SECURITY_SCAN_BLOCKONCRITICAL` | `false` | Fail deployments with CRITICAL findings (Compose: `CLOUDFLOW_SCAN_BLOCK_ON_CRITICAL`) |
| `CLOUDFLOW_SECURITY_SCAN_IMAGE`, `..._TIMEOUT`, `..._CACHEVOLUME` | `aquasec/trivy:0.74.0`, `10m`, `cloudflow-trivy-cache` | Scanner image, per-scan timeout, and the volume caching Trivy's vulnerability DB |
| `CLOUDFLOW_RATELIMIT_ENABLED` | `true` | Per-client token buckets (Compose: `CLOUDFLOW_RATE_LIMIT_ENABLED`) |
| `CLOUDFLOW_STORAGE_ENABLED` | `false` | Artifact storage in S3 |
| `CLOUDFLOW_STORAGE_BUCKET`, `..._REGION`, `..._ENDPOINT`, `..._PATHSTYLEACCESS` | `cloudflow-artifacts`, `us-east-1`, empty, `false` | Bucket and S3 endpoint (empty endpoint = Amazon S3) |
| `CLOUDFLOW_STORAGE_ACCESSKEY` / `..._SECRETKEY` | empty | Static keys; empty uses the AWS default credential chain (instance role, `AWS_*` variables) |
| `CLOUDFLOW_STORAGE_TIMEOUT`, `..._MAXOBJECTSIZEMB` | `30s`, `50` | Per-call timeout; larger artifacts are skipped |
| `CLOUDFLOW_KUBERNETES_ENABLED` | `false` | Enable the Kubernetes target |
| `CLOUDFLOW_KUBERNETES_KUBECONFIG` / `..._MASTERURL` | empty | Kubeconfig path (empty: `KUBECONFIG`, `~/.kube/config`, or in-cluster) and optional API server URL override |
| `CLOUDFLOW_KUBERNETES_NAMESPACE` | `cloudflow-apps` | Namespace for application workloads (created if missing) |
| `CLOUDFLOW_KUBERNETES_NODEHOST` / `..._PUBLICHOST` | `localhost` / `localhost` | Host the backend probes NodePorts on / host in application URLs |
| `CLOUDFLOW_KUBERNETES_STORAGECLASS`, `..._VOLUMESIZE`, `..._STARTTIMEOUT` | cluster default, `1Gi`, `5m` | Data volumes and pod start timeout |
| `CLOUDFLOW_RATELIMIT_APIPERMINUTE`, `..._AIPERMINUTE`, `..._AUTHPERMINUTE`, `..._PIPELINEHOOKPERMINUTE`, `..._WEBHOOKPERMINUTE` | `600`, `20`, `30`, `60`, `300` | Budgets per minute (see api.md → Rate limits) |

### frontend

| Variable | Default | Description |
| --- | --- | --- |
| `NEXT_PUBLIC_API_BASE_URL` | `http://localhost:8080` | Backend base URL (inlined at build time) |

### ai-service

| Variable | Default | Description |
| --- | --- | --- |
| `AI_SERVICE_ENVIRONMENT` | `local` | Environment name |
| `AI_SERVICE_LOG_LEVEL` | `INFO` | Log level |
| `AI_SERVICE_DATABASE_URL` | `postgresql://cloudflow:cloudflow@localhost:5432/cloudflow` | PostgreSQL with pgvector (the service owns schema `ai`) |
| `AI_SERVICE_INTERNAL_TOKEN` | — | Must equal the backend's `CLOUDFLOW_AI_INTERNAL_TOKEN` |
| `AI_SERVICE_OPENAI_API_KEY` | — | OpenAI API key |
| `AI_SERVICE_CHAT_MODEL` / `AI_SERVICE_EMBEDDING_MODEL` | `gpt-5.4-mini` / `text-embedding-3-small` | Models |

### backend (AI)

| Variable | Default | Description |
| --- | --- | --- |
| `CLOUDFLOW_AI_BASE_URL` | `http://localhost:8000` | AI service URL (`http://ai-service:8000` in Compose) |
| `CLOUDFLOW_AI_INTERNAL_TOKEN` | empty | Shared secret. Empty disables AI features. |

## 4. How user applications are run

- Every deployment builds (or, for rollbacks, reuses) an image, pushes it to the registry, and
  starts a new container on the `cloudflow-apps` network with a random published host port.
- The previous container keeps serving until the new one passes its health check. Only then is the
  old container stopped and removed, so a failed deployment never causes downtime.
- `/data` inside the container is a named volume per environment (`cf-data-<environment-id>`) that
  persists across deployments.
- Containers get the configured CPU and memory limits and an `on-failure` restart policy (3
  attempts). Generated images run as a non-root user.

### CI/CD with GitHub Actions

1. In the project's **CI/CD** tab, pick an environment, review the generated workflow, and click
   **Commit workflow**. CloudFlow commits `.github/workflows/cloudflow-<env>.yml` to the
   environment's branch.
2. In the environment's **Settings** tab, create a deploy token (shown once).
3. In GitHub (Settings → Secrets and variables → Actions), add `CLOUDFLOW_URL` (the public API URL
   shown in the preview) and `CLOUDFLOW_DEPLOY_TOKEN_<ENV>`.
4. Push to the branch. Runs appear under **View runs**, synced on demand or live through the
   optional webhook.

GitHub-hosted runners must be able to reach `CLOUDFLOW_URL`, so a CloudFlow running only on
`localhost` needs a public endpoint (Phase 10 deployment, or a tunnel) for the deploy stage.

### Kubernetes target (Phase 10)

Any environment can run on Kubernetes instead of Docker: *Environment → Configuration → Deployment
target*. Images are still built (and scanned) with Docker and pushed to the registry; the cluster
pulls them from there.

**Local cluster.** The `kubernetes` Compose profile starts a single-node k3s cluster that pulls
`localhost:5000/...` images from the bundled registry through a mirror (`infrastructure/k3s/registries.yaml`):

```bash
cd infrastructure
echo "CLOUDFLOW_KUBERNETES_ENABLED=true" >> .env
docker compose --profile kubernetes up -d
```

Applications get NodePorts in 30000–30049, published on `localhost`. k3s runs as a privileged
container, so use this profile for local development only.

**Existing cluster** (EKS, AKS, GKE, …): mount a kubeconfig into the backend container and set
`CLOUDFLOW_KUBERNETES_KUBECONFIG`, `..._NODEHOST` (an address of a node the backend can reach), and
`..._PUBLICHOST`. The cluster must be able to pull from `CLOUDFLOW_REGISTRY` (use a registry both can
reach, e.g. Amazon ECR, and configure image pull access on the nodes). Minimum permissions in the
namespace: `deployments` (apps) and `services`, `persistentvolumeclaims` (create, get, delete),
`pods`, `pods/log` (get, list, watch); cluster-wide: `namespaces` (get, create) and `nodes/proxy`
(get, for metrics).

### Artifact storage (Phase 10)

The backend stores build outputs (the Dockerfile and vulnerability report of every build), generated
files (committed workflows and approved AI files), and the complete log of every finished deployment
in one S3 bucket. They are listed under *Project → Artifacts* and on each deployment page, and are
downloaded through the API. Locally the `object-storage` service (SeaweedFS, S3-compatible) holds
them in the `object-storage-data` volume; set `CLOUDFLOW_S3_ACCESS_KEY` / `..._SECRET_KEY` in `.env`
(`openssl rand -hex 16` / `openssl rand -hex 32`). Storage is best effort: if the store is down,
deployments continue and the backend logs a warning.

### Security hardening (Phase 9)

- **Image scanning.** Between build and push the engine runs `aquasec/trivy` as a short-lived
  container that reads the new image through the Docker socket. The first scan downloads Trivy's
  vulnerability database (a few hundred MB) into the `cloudflow-trivy-cache` volume; later scans reuse
  it. Results appear in the deployment log (`SCAN` phase) and on the deployment page. With
  `block-on-critical` on, an image with CRITICAL findings is never pushed or started, and the
  previous container keeps running.
- **Deployed containers** run as a non-root user (generated Dockerfiles), with the configured CPU and
  memory limits, `no-new-privileges`, and at most 512 processes.
- **Platform containers** (Compose) all use `no-new-privileges` and have memory/CPU limits. Only the
  frontend, backend API, and registry ports are published. The management port (8081 inside the
  backend container) and the AI service stay on the internal network.
- **Audit log** — Organization → *Audit log* tab (Owners and Admins).

### End-to-end tests (Phase 9)

`frontend/e2e` holds a Playwright suite that runs against the full Docker stack. It seeds a user and
refresh token directly in PostgreSQL so it does not need a real GitHub login; the GitHub sign-in
redirect itself is also checked. See `frontend/e2e/README.md`:

```bash
cd infrastructure && docker compose up -d --build
cd ../frontend && npx playwright install chromium
E2E_API_URL=http://localhost:${BACKEND_PORT:-8080} npm run e2e
```

## 5. Container images

| Image | Base | Notes |
| --- | --- | --- |
| backend | `eclipse-temurin:21-jre-alpine` | Built in `gradle:9.7.1-jdk21-alpine`, which matches the wrapper version (keep them in sync). Layered Spring Boot jar, non-root `cloudflow` user, liveness health check |
| frontend | `node:22-alpine` | Next.js `standalone` output, non-root `node` user |
| ai-service | `python:3.12-slim` | uv-managed virtualenv from the lockfile, non-root `cloudflow` user |

## 6. CI

GitHub Actions workflows in `.github/workflows/` run on pull requests and on pushes to `main`,
filtered by path:

| Workflow | Checks |
| --- | --- |
| `backend.yml` | Spotless formatting, compile with `-Werror`, tests (Testcontainers PostgreSQL), Docker build |
| `frontend.yml` | Prettier, ESLint, TypeScript, Vitest unit tests, production build, Docker build |
| `ai-service.yml` | Ruff lint + format, pytest against a real pgvector database (Testcontainers), Docker build |
| `infrastructure.yml` | `docker compose config` validation |
| `e2e.yml` | Builds and starts the full stack, then runs the Playwright suite |

### Troubleshooting: iCloud-synced folders

If the repository lives in an iCloud-synced folder (for example `~/Desktop` or `~/Documents` with
"Desktop & Documents" sync on), iCloud can create conflict copies such as `Foo 2.class` inside
`build/` or `.next/` while builds run, and the build then fails. Keep the repository outside synced
folders (e.g. `~/dev/cloudflow`). If that is not possible, run `./gradlew clean` and remove
`frontend/.next` before building.

## 7. Production deployment on AWS (Phase 10)

CloudFlow itself runs as Docker containers (`docker-compose.yml` plus the production override
`docker-compose.prod.yml`). The reference setup on AWS:

```mermaid
flowchart LR
    user([Browser]) -->|HTTPS 443| alb[Application Load Balancer<br/>ACM certificate]
    gh[(GitHub)] -->|OAuth callback, webhooks,<br/>Actions deploy hook| alb
    subgraph vpc[VPC]
        alb -->|:3000 app.example.com| ec2
        alb -->|:8080 api.example.com| ec2
        subgraph ec2[EC2 instance · Docker]
            fe[frontend] 
            be[backend]
            ai[ai-service]
            reg[registry]
            apps[deployed apps]
        end
        be --> rds[(RDS PostgreSQL 17<br/>+ pgvector)]
        ai --> rds
    end
    be -->|IAM instance role| s3[(S3 bucket)]
    ai -->|HTTPS| openai[(OpenAI API)]
```

| AWS service | Used for | Notes |
| --- | --- | --- |
| EC2 | Docker host for all CloudFlow containers and the Docker deployment target | e.g. `t3.large` (2 vCPU, 8 GB) or larger, Amazon Linux 2023 or Ubuntu 24.04, 50+ GB gp3 for images |
| RDS for PostgreSQL | System of record and vector store | PostgreSQL 17, Multi-AZ for production, automated backups. pgvector is supported on RDS; the AI service runs `CREATE EXTENSION vector`, so its user needs `rds_superuser` once (or create the extension manually). |
| S3 | Artifacts | Private bucket, Block Public Access on, SSE-S3 or SSE-KMS encryption, optional lifecycle rule (e.g. archived logs → Glacier after 90 days) |
| Application Load Balancer | TLS termination and routing | Host rules: `app.example.com` → target group on port 3000 (health check `GET /`, 200), `api.example.com` → port 8080 (health check `GET /oauth2/authorization/github`, success code 302; Actuator stays internal). WebSockets work through the ALB; set the idle timeout to at least 120 s for log streams. |
| IAM | Instance role for S3 | Policy on `arn:aws:s3:::<bucket>` and `/*`: `s3:PutObject`, `s3:GetObject`, `s3:DeleteObject`, `s3:ListBucket` |
| ACM / Route 53 | Certificates and DNS | One certificate for both host names |

**Security groups.** ALB: 443 from the internet. EC2: 3000 and 8080 from the ALB security group only;
SSH (or better, SSM Session Manager) from your network. RDS: 5432 from the EC2 security group only.
Deployed applications publish random host ports; to expose them, add ALB rules or open the port range
to trusted networks.

**Steps**

1. Create the S3 bucket, the RDS instance (database `cloudflow`), and the IAM role; attach the role
   to the EC2 instance.
2. Install Docker Engine and the Compose plugin on the instance and clone the repository.
3. Create the GitHub OAuth App with callback `https://api.example.com/login/oauth2/code/github` and
   homepage `https://app.example.com`.
4. In `infrastructure/.env` set, in addition to the secrets from §3:
   ```bash
   FRONTEND_URL=https://app.example.com
   PUBLIC_API_BASE_URL=https://api.example.com
   CLOUDFLOW_PUBLIC_API_URL=https://api.example.com   # used by GitHub Actions deploy hooks
   CLOUDFLOW_PUBLIC_HOST=apps.example.com             # host name of deployed applications
   CLOUDFLOW_DATABASE_URL=jdbc:postgresql://<rds-endpoint>:5432/cloudflow?sslmode=require
   CLOUDFLOW_DATABASE_USERNAME=cloudflow
   CLOUDFLOW_DATABASE_PASSWORD=<password>
   CLOUDFLOW_AI_DATABASE_URL=postgresql://cloudflow:<password>@<rds-endpoint>:5432/cloudflow?sslmode=require
   CLOUDFLOW_S3_BUCKET=<bucket>
   CLOUDFLOW_S3_REGION=<region>
   DOCKER_SOCKET_GID=<getent group docker | cut -d: -f3>
   ```
5. Start CloudFlow:
   ```bash
   cd infrastructure
   docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
   ```
   The production override disables the bundled PostgreSQL and object store (profiles
   `bundled-database`, `bundled-storage`), sets `CLOUDFLOW_COOKIE_SECURE=true`, and switches storage
   to Amazon S3 with the instance role.
6. Register the EC2 instance in both ALB target groups and point the DNS names at the ALB.

**Kubernetes on AWS.** To deploy applications to Amazon EKS instead of the EC2 Docker engine, push
images to Amazon ECR (`CLOUDFLOW_REGISTRY=<account>.dkr.ecr.<region>.amazonaws.com`, with the Docker
daemon logged in to ECR), give the backend a kubeconfig for the cluster, and set
`CLOUDFLOW_KUBERNETES_ENABLED=true` (see "Kubernetes target").

**Operations.** Back up RDS with automated snapshots; S3 versioning protects artifacts. Upgrades:
`git pull` and rerun step 5 (Flyway and the AI service migrate their schemas on startup). Scaling
beyond one backend instance needs Redis for rate limits and the WebSocket broker
([future-scope.md](future-scope.md)).
