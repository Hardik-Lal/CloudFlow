import { apiFetch } from "./client";
import type { Deployment, DeploymentLogLine, Page } from "./types";

export const deploymentsApi = {
  trigger: (environmentId: string, commitSha?: string) =>
    apiFetch<Deployment>(`/api/v1/environments/${environmentId}/deployments`, {
      method: "POST",
      body: commitSha ? { commitSha } : {},
    }),
  forEnvironment: (environmentId: string, page = 0, size = 20) =>
    apiFetch<Page<Deployment>>(
      `/api/v1/environments/${environmentId}/deployments?page=${page}&size=${size}`,
    ),
  forProject: (projectId: string, page = 0, size = 20) =>
    apiFetch<Page<Deployment>>(
      `/api/v1/projects/${projectId}/deployments?page=${page}&size=${size}`,
    ),
  get: (deploymentId: string) => apiFetch<Deployment>(`/api/v1/deployments/${deploymentId}`),
  logs: (deploymentId: string, afterId: number, limit = 1000) =>
    apiFetch<DeploymentLogLine[]>(
      `/api/v1/deployments/${deploymentId}/logs?afterId=${afterId}&limit=${limit}`,
    ),
  rollback: (deploymentId: string) =>
    apiFetch<Deployment>(`/api/v1/deployments/${deploymentId}/rollback`, { method: "POST" }),
  cancel: (deploymentId: string) =>
    apiFetch<Deployment>(`/api/v1/deployments/${deploymentId}/cancel`, { method: "POST" }),
};
