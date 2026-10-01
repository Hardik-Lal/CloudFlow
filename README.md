# CloudFlow

**AI-assisted Internal Developer Platform.**

> The aim of CloudFlow is to develop an AI-assisted Internal Developer Platform that unifies
> application development, configuration, deployment, CI/CD, monitoring, logging, and
> troubleshooting into a single platform, reducing operational complexity and enabling developers to
> build and deploy software more efficiently and reliably.

CloudFlow brings project management, environment configuration, deployment to Docker or Kubernetes, CI/CD,
monitoring, and logging together in one platform. An AI assistance layer adds contextual
troubleshooting and project-aware (RAG) answers on top. CloudFlow is **not** an AI chatbot: AI is an
intelligent layer over the development and deployment lifecycle.

## Repository layout

```
cloudflow/
├── frontend/         Next.js · TypeScript · Tailwind CSS · shadcn/ui · TanStack Query · Zustand
├── backend/          Java 21 · Spring Boot · Spring Security · Gradle · PostgreSQL (modular monolith)
├── ai-service/       Python · FastAPI · OpenAI API · pgvector
├── infrastructure/   Docker Compose stack (+ production override, local k3s and S3-compatible storage)
├── docs/             Requirements, architecture, database, API, deployment, future scope
└── .github/          CI workflows and PR template
```

## Quick start

```bash
cd infrastructure
cp .env.example .env            # set POSTGRES_PASSWORD, GitHub OAuth App, secrets, S3 keys (see docs/deployment.md)
docker compose up --build -d
```

- Frontend: http://localhost:3000
- API: http://localhost:8080/api/v1 (health checks are internal: `docker compose exec backend wget -qO- localhost:8081/actuator/health`)
- Image registry: http://127.0.0.1:5000/v2/_catalog
- Prometheus: http://127.0.0.1:9090 (Grafana: `docker compose --profile grafana up -d` → http://127.0.0.1:3001)
- Kubernetes target (optional): set `CLOUDFLOW_KUBERNETES_ENABLED=true`, then `docker compose --profile kubernetes up -d`
- Production on AWS (EC2, RDS, S3, ALB): `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d`, see [docs/deployment.md §7](docs/deployment.md#7-production-deployment-on-aws-phase-10)

To run each service from source, see [docs/deployment.md](docs/deployment.md#running-services-from-source).

## Documentation

| Document | Contents |
| --- | --- |
| [docs/requirements.md](docs/requirements.md) | Functional and non-functional requirements, roles and permissions |
| [docs/architecture.md](docs/architecture.md) | System, container, and component architecture, data flows, decisions |
| [docs/database.md](docs/database.md) | ER model, schema ownership, conventions |
| [docs/api.md](docs/api.md) | REST and WebSocket API contracts |
| [docs/deployment.md](docs/deployment.md) | Deployment boundaries, configuration, local setup, Kubernetes, artifact storage, CI, production on AWS |
| [docs/future-scope.md](docs/future-scope.md) | Exploration of preview environments, multi-cloud, canary releases, cost optimization |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Branching strategy, commit conventions, coding standards |

## Implementation progress

| Phase | Scope | Status |
| --- | --- | --- |
| 1 | Architecture and requirements, monorepo, CI | ✅ Done |
| 2 | Authentication (GitHub OAuth, JWT) and organizations (RBAC) | ✅ Done |
| 3 | GitHub integration and project management | ✅ Done |
| 4 | Environment and configuration management | ✅ Done |
| 5 | Containerization and deployment engine | ✅ Done |
| 6 | CI/CD pipeline (GitHub Actions) | ✅ Done |
| 7 | Monitoring and real-time logs | ✅ Done |
| 8 | AI and RAG layer | ✅ Done |
| 9 | Security, reliability, and testing | ✅ Done |
| 10 | Cloud deployment and advanced scope | ✅ Done |
