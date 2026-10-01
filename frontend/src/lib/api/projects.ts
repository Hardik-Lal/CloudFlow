import { apiFetch } from "./client";
import type { Branch, Commit, Project, ProjectSummary, PullRequest } from "./types";

export interface CreateProjectInput {
  repositoryFullName: string;
  name?: string;
  slug?: string;
  description?: string;
}

export interface UpdateProjectInput {
  name: string;
  description?: string | null;
}

export const projectsApi = {
  list: (organizationId: string) =>
    apiFetch<ProjectSummary[]>(`/api/v1/organizations/${organizationId}/projects`),
  create: (organizationId: string, input: CreateProjectInput) =>
    apiFetch<Project>(`/api/v1/organizations/${organizationId}/projects`, {
      method: "POST",
      body: input,
    }),
  get: (projectId: string) => apiFetch<Project>(`/api/v1/projects/${projectId}`),
  update: (projectId: string, input: UpdateProjectInput) =>
    apiFetch<Project>(`/api/v1/projects/${projectId}`, { method: "PATCH", body: input }),
  remove: (projectId: string) =>
    apiFetch<void>(`/api/v1/projects/${projectId}`, { method: "DELETE" }),
  sync: (projectId: string) =>
    apiFetch<Project>(`/api/v1/projects/${projectId}/sync`, { method: "POST" }),
  branches: (projectId: string) => apiFetch<Branch[]>(`/api/v1/projects/${projectId}/branches`),
  commits: (projectId: string, branch?: string) =>
    apiFetch<Commit[]>(
      `/api/v1/projects/${projectId}/commits${branch ? `?branch=${encodeURIComponent(branch)}` : ""}`,
    ),
  pulls: (projectId: string, state: "open" | "closed" | "all" = "open") =>
    apiFetch<PullRequest[]>(`/api/v1/projects/${projectId}/pulls?state=${state}`),
};
