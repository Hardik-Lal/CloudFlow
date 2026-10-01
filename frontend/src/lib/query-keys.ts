/** Central TanStack Query keys so invalidation stays consistent across features. */
export const queryKeys = {
  organizations: {
    all: ["organizations"] as const,
    detail: (organizationId: string) => ["organizations", organizationId] as const,
    members: (organizationId: string) => ["organizations", organizationId, "members"] as const,
    projects: (organizationId: string) => ["organizations", organizationId, "projects"] as const,
    audit: (organizationId: string, page: number, action: string) =>
      ["organizations", organizationId, "audit", page, action] as const,
  },
  projects: {
    detail: (projectId: string) => ["projects", projectId] as const,
    branches: (projectId: string) => ["projects", projectId, "branches"] as const,
    commits: (projectId: string, branch: string | undefined) =>
      ["projects", projectId, "commits", branch ?? "default"] as const,
    pulls: (projectId: string) => ["projects", projectId, "pulls"] as const,
    environments: (projectId: string) => ["projects", projectId, "environments"] as const,
    deployments: (projectId: string, page: number) =>
      ["projects", projectId, "deployments", page] as const,
    pipelines: (projectId: string) => ["projects", projectId, "pipelines"] as const,
    knowledge: (projectId: string) => ["projects", projectId, "knowledge"] as const,
    suggestions: (projectId: string) => ["projects", projectId, "suggestions"] as const,
    artifacts: (projectId: string, deploymentId?: string) =>
      ["projects", projectId, "artifacts", deploymentId ?? "all"] as const,
  },
  pipelines: {
    detail: (pipelineId: string) => ["pipelines", pipelineId] as const,
    runs: (pipelineId: string) => ["pipelines", pipelineId, "runs"] as const,
  },
  environments: {
    detail: (environmentId: string) => ["environments", environmentId] as const,
    variables: (environmentId: string) => ["environments", environmentId, "variables"] as const,
    config: (environmentId: string) => ["environments", environmentId, "config"] as const,
    validation: (environmentId: string) => ["environments", environmentId, "validation"] as const,
    deployments: (environmentId: string, page: number) =>
      ["environments", environmentId, "deployments", page] as const,
    deployTokens: (environmentId: string) =>
      ["environments", environmentId, "deploy-tokens"] as const,
    metrics: (environmentId: string) => ["environments", environmentId, "metrics"] as const,
    events: (environmentId: string) => ["environments", environmentId, "events"] as const,
  },
  deployments: {
    detail: (deploymentId: string) => ["deployments", deploymentId] as const,
  },
  configTemplates: (appType: string) => ["config-templates", appType] as const,
  deploymentTargets: ["deployment-targets"] as const,
  github: {
    repositories: ["github", "repositories"] as const,
  },
};
