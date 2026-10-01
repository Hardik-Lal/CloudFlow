export type Role = "OWNER" | "ADMIN" | "DEVELOPER" | "VIEWER";

export const ROLES: readonly Role[] = ["OWNER", "ADMIN", "DEVELOPER", "VIEWER"];

export type Permission =
  | "ORG_READ"
  | "ORG_UPDATE"
  | "ORG_DELETE"
  | "MEMBER_READ"
  | "MEMBER_MANAGE"
  | "PROJECT_READ"
  | "PROJECT_WRITE"
  | "PROJECT_DELETE"
  | "ENV_READ"
  | "ENV_WRITE"
  | "ENV_WRITE_PRODUCTION"
  | "DEPLOYMENT_READ"
  | "DEPLOYMENT_TRIGGER"
  | "DEPLOYMENT_TRIGGER_PRODUCTION"
  | "PIPELINE_READ"
  | "PIPELINE_WRITE"
  | "AI_USE"
  | "AI_APPLY"
  | "AUDIT_READ";

export interface User {
  id: string;
  username: string;
  displayName: string | null;
  email: string | null;
  avatarUrl: string | null;
}

export interface AuthTokenResponse {
  accessToken: string;
  tokenType: "Bearer";
  expiresIn: number;
  user: User;
}

export interface Organization {
  id: string;
  name: string;
  slug: string;
  role: Role;
  permissions: Permission[];
  createdAt: string;
}

export interface Member {
  userId: string;
  username: string;
  displayName: string | null;
  avatarUrl: string | null;
  role: Role;
  joinedAt: string;
}

export type AppType = "DOCKERFILE" | "JAVA_MAVEN" | "JAVA_GRADLE" | "NODE" | "PYTHON" | "UNKNOWN";

export interface Repository {
  githubRepoId: number;
  owner: string;
  name: string;
  fullName: string;
  htmlUrl: string;
  defaultBranch: string;
  isPrivate: boolean;
  language: string | null;
  lastSyncedAt: string;
}

export interface ProjectSummary {
  id: string;
  organizationId: string;
  name: string;
  slug: string;
  description: string | null;
  appType: AppType;
  repository: Repository;
  updatedAt: string;
}

export interface Project extends Omit<ProjectSummary, "updatedAt"> {
  role: Role;
  permissions: Permission[];
  createdAt: string;
  updatedAt: string;
}

export interface GithubRepository {
  id: number;
  owner: string;
  name: string;
  fullName: string;
  description: string | null;
  htmlUrl: string;
  defaultBranch: string;
  isPrivate: boolean;
  language: string | null;
  pushedAt: string | null;
}

export interface GithubRepositoryPage {
  items: GithubRepository[];
  page: number;
  perPage: number;
  hasNext: boolean;
}

export interface Branch {
  name: string;
  headCommitSha: string;
  isProtected: boolean;
}

export interface Commit {
  sha: string;
  shortSha: string;
  message: string;
  authorName: string | null;
  authorLogin: string | null;
  authorAvatarUrl: string | null;
  committedAt: string | null;
  htmlUrl: string;
}

export interface PullRequest {
  number: number;
  title: string;
  state: string;
  draft: boolean;
  authorLogin: string | null;
  authorAvatarUrl: string | null;
  headBranch: string;
  baseBranch: string;
  htmlUrl: string;
  createdAt: string;
  updatedAt: string;
}

export type EnvironmentType = "DEVELOPMENT" | "STAGING" | "PRODUCTION";

export const ENVIRONMENT_TYPES: readonly EnvironmentType[] = [
  "DEVELOPMENT",
  "STAGING",
  "PRODUCTION",
];

export type ConfigTemplate = "JAVA" | "NODE" | "PYTHON" | "DOCKER";

export type DeploymentTarget = "DOCKER" | "KUBERNETES";

export interface DeploymentTargetInfo {
  target: DeploymentTarget;
  label: string;
  /** Whether this CloudFlow installation is configured for the target. */
  available: boolean;
}

export interface DeploymentConfig {
  template: ConfigTemplate;
  runtimeVersion: string | null;
  buildCommand: string | null;
  startCommand: string | null;
  dockerfilePath: string;
  containerPort: number;
  healthCheckPath: string;
  cpuLimit: number | null;
  memoryLimitMb: number | null;
  target: DeploymentTarget;
  updatedAt: string | null;
}

export type DeploymentConfigInput = Omit<DeploymentConfig, "updatedAt">;

export interface Environment {
  id: string;
  projectId: string;
  type: EnvironmentType;
  branch: string;
  config: DeploymentConfig | null;
  variableCount: number;
  secretCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface Variable {
  key: string;
  /** null for secrets: secret values are write-only. */
  value: string | null;
  secret: boolean;
  updatedAt: string;
}

export interface ValidationIssue {
  field: string;
  message: string;
}

export interface ValidationResult {
  valid: boolean;
  errors: ValidationIssue[];
  warnings: ValidationIssue[];
}

export interface ConfigTemplateInfo {
  template: ConfigTemplate;
  label: string;
  description: string;
  recommended: boolean;
  defaults: DeploymentConfig;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export type DeploymentStatus =
  | "QUEUED"
  | "BUILDING"
  | "DEPLOYING"
  | "HEALTH_CHECK"
  | "SUCCEEDED"
  | "FAILED"
  | "CANCELLED"
  | "ROLLED_BACK";

export const IN_PROGRESS_STATUSES: readonly DeploymentStatus[] = [
  "QUEUED",
  "BUILDING",
  "DEPLOYING",
  "HEALTH_CHECK",
];

export type TriggerType = "MANUAL" | "PIPELINE" | "ROLLBACK";

export interface Deployment {
  id: string;
  projectId: string;
  environmentId: string;
  status: DeploymentStatus;
  triggerType: TriggerType;
  triggeredBy: string | null;
  branch: string;
  commitSha: string | null;
  commitMessage: string | null;
  appType: AppType | null;
  imageTag: string | null;
  imageId: string | null;
  containerName: string | null;
  hostPort: number | null;
  url: string | null;
  rollbackOfId: string | null;
  active: boolean;
  failureReason: string | null;
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
  /** GitHub Actions run id when triggered by a pipeline. */
  pipelineRunId: number | null;
  scanStatus: ScanStatus | null;
  vulnerabilitiesCritical: number | null;
  vulnerabilitiesHigh: number | null;
  target: DeploymentTarget;
}

export type LogPhase = "SOURCE" | "BUILD" | "SCAN" | "PUSH" | "DEPLOY" | "HEALTH_CHECK" | "RUNTIME";
export type LogLevel = "INFO" | "WARN" | "ERROR";

export interface DeploymentLogLine {
  id: number;
  phase: LogPhase;
  level: LogLevel;
  message: string;
  loggedAt: string;
}

export interface PipelineJob {
  name: string;
  status: string;
  conclusion: string | null;
  htmlUrl: string | null;
  startedAt: string | null;
  completedAt: string | null;
}

export interface PipelineRun {
  id: string;
  githubRunId: number;
  runNumber: number;
  runAttempt: number;
  event: string;
  /** GitHub status: queued, in_progress, completed, … */
  status: string;
  /** Set when completed: success, failure, cancelled, … */
  conclusion: string | null;
  headBranch: string | null;
  headSha: string;
  commitMessage: string | null;
  actor: string | null;
  htmlUrl: string;
  startedAt: string | null;
  completedAt: string | null;
  jobs: PipelineJob[];
}

export interface Pipeline {
  id: string;
  projectId: string;
  environmentId: string;
  environmentType: EnvironmentType;
  branch: string;
  workflowPath: string;
  template: ConfigTemplate;
  committedSha: string | null;
  workflowUrl: string;
  actionsUrl: string;
  lastSyncedAt: string | null;
  latestRun: PipelineRun | null;
  createdAt: string;
}

export interface RequiredSecret {
  name: string;
  description: string;
  value: string | null;
}

export interface PipelinePreview {
  environmentId: string;
  workflowPath: string;
  branch: string;
  content: string;
  fileExists: boolean;
  secrets: RequiredSecret[];
}

export interface DeployToken {
  id: string;
  environmentId: string;
  name: string;
  tokenPrefix: string;
  createdBy: string | null;
  createdAt: string;
  lastUsedAt: string | null;
  revokedAt: string | null;
}

export interface CreatedDeployToken {
  deployToken: DeployToken;
  /** Shown once; cannot be retrieved again. */
  token: string;
  secretName: string;
}

export interface MetricSample {
  timestamp: string;
  deploymentId: string;
  running: boolean;
  healthy: boolean | null;
  cpuPercent: number;
  memoryBytes: number;
  memoryLimitBytes: number;
  networkRxBytes: number;
  networkTxBytes: number;
  uptimeSeconds: number;
  restartCount: number;
  responseTimeMs: number | null;
  statusCode: number | null;
}

export type ServiceStatus = "NOT_DEPLOYED" | "UNKNOWN" | "UP" | "DEGRADED" | "DOWN";

export interface HealthSummary {
  window: string;
  checks: number;
  uptimePercent: number | null;
  errorRatePercent: number | null;
  averageResponseTimeMs: number | null;
  p95ResponseTimeMs: number | null;
}

export interface EnvironmentMetrics {
  environmentId: string;
  deploymentId: string | null;
  status: ServiceStatus;
  current: MetricSample | null;
  health: HealthSummary;
  history: MetricSample[];
}

export type EventSeverity = "INFO" | "WARN" | "ERROR";

export interface EnvironmentEvent {
  id: number;
  type: string;
  severity: EventSeverity;
  message: string;
  deploymentId: string | null;
  createdAt: string;
}

export interface RuntimeLogLine {
  deploymentId: string;
  message: string;
  receivedAt: string;
}

export interface DeploymentStatusMessage {
  deploymentId: string;
  status: DeploymentStatus;
  failureReason: string | null;
}

export interface Evidence {
  source: string;
  reference: string;
  excerpt: string;
}

export type Confidence = "HIGH" | "MEDIUM" | "LOW";

export interface AssistantAnswer {
  answer: string;
  likelyCause: string | null;
  evidence: Evidence[];
  recommendedActions: string[];
  confidence: Confidence;
  confidenceExplanation: string;
}

export interface KnowledgeStatus {
  enabled: boolean;
  documents: number;
  chunks: number;
  lastIndexedAt: string | null;
}

export interface ReindexResult {
  collected: number;
  indexed: number;
  unchanged: number;
  removed: number;
}

export type SuggestionType = "DOCKERFILE" | "ENV_TEMPLATE" | "WORKFLOW" | "DOCUMENTATION";
export type SuggestionStatus = "PENDING" | "APPLIED" | "REJECTED";

export interface Suggestion {
  id: string;
  projectId: string;
  environmentId: string | null;
  type: SuggestionType;
  status: SuggestionStatus;
  filePath: string;
  content: string;
  explanation: string | null;
  instructions: string | null;
  createdBy: string | null;
  reviewedBy: string | null;
  reviewedAt: string | null;
  result: string | null;
  createdAt: string;
}

export type ScanStatus = "PASSED" | "VULNERABLE" | "BLOCKED" | "ERROR" | "SKIPPED";

export interface AuditLogEntry {
  id: number;
  action: string;
  actorId: string | null;
  actorUsername: string | null;
  resourceType: string;
  resourceId: string | null;
  details: Record<string, string>;
  ipAddress: string | null;
  createdAt: string;
}

export type ArtifactKind = "BUILD_ARTIFACT" | "GENERATED_FILE" | "ARCHIVED_LOG";

export interface Artifact {
  id: string;
  projectId: string;
  environmentId: string | null;
  deploymentId: string | null;
  kind: ArtifactKind;
  /** File name, or repository path for generated files. */
  name: string;
  contentType: string;
  sizeBytes: number;
  sha256: string;
  createdBy: string | null;
  createdAt: string;
}
