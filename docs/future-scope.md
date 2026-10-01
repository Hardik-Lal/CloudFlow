# CloudFlow — Future Scope

Phase 10 of the implementation plan asks to *explore* preview environments, multi-cloud support,
canary releases, and cost optimization. This document records that exploration: how each feature
would fit the current architecture, what already exists to build on, what would have to change, and
the open risks. None of these features is implemented; the Kubernetes target (Phase 10) is the
prototype that most of them build on.

The plan's longer list of future work (§9) is summarized at the end.

## 1. Pull-request preview environments

**Goal.** Every pull request gets a temporary environment with its own URL, created when the PR opens
and removed when it closes.

**What exists.** GitHub webhooks are already received and verified (`/api/v1/webhooks/github`,
Phase 6); pull requests are listed through the GitHub API (Phase 3); deployments of any commit SHA,
health checks, logs, and rollback are generic (Phase 5); both targets can run many workloads side by
side (the blue/green flow already runs two at once).

**Design.**

| Part | Change |
| --- | --- |
| Data model | New `EnvironmentType.PREVIEW` with a `pull_request_number` column; the "one environment per type" rule stays for DEVELOPMENT/STAGING/PRODUCTION only. |
| Trigger | Handle `pull_request` webhook events: `opened`/`synchronize` → create or update the preview environment (config copied from Development, branch = PR head) and deploy the head SHA; `closed` → remove the workload, then the environment. |
| Access | Previews inherit Development permissions (`DEPLOYMENT_TRIGGER`), never production secrets. Secrets are copied only when explicitly marked "available to previews". |
| URL | Docker: `pr-<n>-<project>.<public host>` through a reverse proxy; Kubernetes: an Ingress per preview. |
| Feedback | Post the URL and status as a GitHub commit status / PR comment (needs the `repo:status` scope, already covered by `repo`). |
| Limits | Maximum previews per project and an idle timeout (see §4) so forgotten PRs do not consume resources. |

**Risks.** Pull requests from forks run untrusted code with the project's build context: previews
must be restricted to branches in the same repository, or require an approval step.

## 2. Multi-cloud support

**Goal.** Deploy applications to AWS, Azure, or Google Cloud and run CloudFlow itself on any of them.

**What exists.** The engine is target-agnostic (`WorkloadRuntime`, Phase 10) and Kubernetes is the
common denominator of the three clouds (EKS, AKS, GKE). Artifact storage speaks only the S3 API.
CloudFlow runs from Docker Compose, so it runs on any VM.

**Design.**

| Part | Change |
| --- | --- |
| Clusters | Replace the single `cloudflow.kubernetes.*` configuration with a `clusters` table (name, provider, kubeconfig/credentials encrypted with the existing `EncryptionService`, registry, public host). The environment's target becomes "cluster X" instead of "KUBERNETES". `RoutingContainerRuntime` keeps one `KubernetesWorkloadRuntime` per cluster; workload ids become `k8s:<cluster>/<namespace>/<name>`. |
| Registries | Per-cluster registry (ECR, ACR, Artifact Registry) with the Docker daemon logged in to each; images are pushed to the target cluster's registry. |
| Credentials | Short-lived cloud credentials (EKS IAM authenticator, AKS/GKE workload identity) instead of static kubeconfigs where possible. |
| Storage | Keep the S3 API: S3 on AWS, S3-compatible gateways elsewhere, or add `ObjectStorage` implementations for Azure Blob / GCS behind the existing interface. |
| Infrastructure as Code | Generating Terraform for these clusters is the plan's separate "IaC generation" item and would reuse the approval flow of AI suggestions (Phase 8). |

**Risks.** Credential sprawl (one set per cluster, stored encrypted and audited); network paths from
the backend to every cluster's API server and nodes; per-provider load balancer and DNS differences.

## 3. Canary releases

**Goal.** Send a small share of traffic to a new version, watch its health, then promote or roll back
automatically.

**What exists.** Blue/green switching with health gates and automatic rollback of a failed start
(Phase 5); per-environment health probes, response time, and error rate (Phase 7); alerts as
environment events.

**Design.**

| Part | Change |
| --- | --- |
| Configuration | `deployment_configs.strategy` = `BLUE_GREEN` (today's behavior) or `CANARY` with steps, e.g. 10 % → 50 % → 100 %, a step duration, and thresholds (max error rate, max p95 response time). |
| Kubernetes | A stable Service selecting both versions and replica ratios for coarse splits, or an Ingress/Gateway API `HTTPRoute` with weights for precise splits. |
| Docker | A reverse proxy in front of both containers with weighted upstreams (the Docker target has no traffic layer today). |
| Analysis | A new deployment status `CANARY` between `HEALTH_CHECK` and `SUCCEEDED`; the monitoring collector already samples every 15 s, so each step compares the canary's probe results with the stable version's and promotes or aborts. |
| AI | The AI analysis (Phase 8) can explain an aborted canary with the collected evidence, matching the plan's "AI-powered deployment risk analysis" item. |

**Risks.** Two versions run against the same database: schema changes must be backward compatible
(expand/contract migrations). Error rates from health probes alone are a weak signal; real canary
analysis needs request metrics from the application or the proxy.

## 4. Cost optimization

**Goal.** Show what each project and environment costs and reduce waste.

**What exists.** CPU and memory limits per environment (Phase 4); actual CPU and memory usage every
15 s in Prometheus (`cloudflow_app_*`, Phase 7); deployment history and artifacts per project.

**Design.**

| Part | Change |
| --- | --- |
| Right-sizing | Compare configured limits with the p95 of observed usage over 7 days and suggest new limits through the existing approval flow ("memory limit 1024 MB, p95 use 180 MB → suggest 256 MB"). |
| Idle environments | Detect environments without traffic (probe-only requests) and offer to stop them on a schedule, e.g. Development outside working hours; previews after N idle days. |
| Cost estimate | Price per vCPU-hour and GB-hour per target (configurable, or from the AWS Price List API) × reserved limits → monthly estimate per environment and project on the dashboard. |
| Storage | S3 lifecycle rules for archived logs and old build artifacts; pruning of unused images in the registry (images of deployments that can no longer be rolled back to). |

**Risks.** Estimates based on limits are not a bill; actual cloud billing (AWS Cost Explorer, tags per
environment) is needed for accurate numbers.

## 5. Scaling CloudFlow itself

The plan's architecture lists Redis (and RabbitMQ "if asynchronous jobs are needed"). Today one
backend instance keeps rate-limit buckets, the STOMP broker, and the deployment queue in memory. To run
several backend instances behind the load balancer:

- Redis for shared rate-limit buckets and as the STOMP relay broker (Spring's `StompBrokerRelay`).
- A persistent job queue for deployments (RabbitMQ, or PostgreSQL `SELECT … FOR UPDATE SKIP LOCKED`
  over the existing `deployments` table, which already stores every state).
- The monitoring collector would run on one instance only (leader election or a scheduler lock).

## 6. Remaining items from the plan's future scope

| Item | Starting point in CloudFlow |
| --- | --- |
| Kubernetes-based deployment and orchestration | Implemented as the second deployment target (Phase 10); autoscaling (HPA) and multiple replicas are the next step. |
| Blue-green deployment and automated rollback | Implemented for failed starts (Phase 5); rollback on post-release health degradation would reuse §3's analysis. |
| Infrastructure-as-Code generation (Terraform) | AI suggestion type `TERRAFORM` using the existing generate → approve → commit flow. |
| AI-powered deployment risk analysis | Index the diff between the running and the new commit and ask the assistant before deploying. |
| Infrastructure drift detection | Compare the environment's stored configuration with the running container / Kubernetes objects in the monitoring loop. |
| Plugin ecosystem (Jira, Slack, SonarQube, Datadog) | Outbound webhooks on the existing application events (`DeploymentStatusChangedEvent`, environment events). |
| Policy-as-Code | Extend the vulnerability `ScanPolicy` and the configuration validator with organization-level rules (e.g. OPA/Rego). |
| Team analytics | Deployment frequency, lead time, change failure rate, and time to restore (DORA metrics) are computable from `deployments` and `pipeline_runs`. |
| Centralized logging (OpenSearch/ELK), OpenTelemetry | Ship archived and runtime logs to OpenSearch; add OpenTelemetry tracing to the backend and AI service. |
