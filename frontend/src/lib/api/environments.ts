import { apiFetch } from "./client";
import type {
  AppType,
  ConfigTemplateInfo,
  DeploymentConfig,
  DeploymentConfigInput,
  DeploymentTargetInfo,
  Environment,
  EnvironmentType,
  ValidationResult,
  Variable,
} from "./types";

export const environmentsApi = {
  list: (projectId: string) =>
    apiFetch<Environment[]>(`/api/v1/projects/${projectId}/environments`),
  create: (projectId: string, type: EnvironmentType, branch?: string) =>
    apiFetch<Environment>(`/api/v1/projects/${projectId}/environments`, {
      method: "POST",
      body: { type, branch },
    }),
  get: (environmentId: string) => apiFetch<Environment>(`/api/v1/environments/${environmentId}`),
  update: (environmentId: string, branch: string) =>
    apiFetch<Environment>(`/api/v1/environments/${environmentId}`, {
      method: "PATCH",
      body: { branch },
    }),
  remove: (environmentId: string) =>
    apiFetch<void>(`/api/v1/environments/${environmentId}`, { method: "DELETE" }),

  variables: (environmentId: string) =>
    apiFetch<Variable[]>(`/api/v1/environments/${environmentId}/variables`),
  putVariable: (environmentId: string, key: string, value: string, secret: boolean) =>
    apiFetch<Variable>(
      `/api/v1/environments/${environmentId}/variables/${encodeURIComponent(key)}`,
      { method: "PUT", body: { value, secret } },
    ),
  deleteVariable: (environmentId: string, key: string) =>
    apiFetch<void>(`/api/v1/environments/${environmentId}/variables/${encodeURIComponent(key)}`, {
      method: "DELETE",
    }),

  config: (environmentId: string) =>
    apiFetch<DeploymentConfig>(`/api/v1/environments/${environmentId}/config`),
  updateConfig: (environmentId: string, config: DeploymentConfigInput) =>
    apiFetch<DeploymentConfig>(`/api/v1/environments/${environmentId}/config`, {
      method: "PUT",
      body: config,
    }),
  validate: (environmentId: string) =>
    apiFetch<ValidationResult>(`/api/v1/environments/${environmentId}/config/validate`, {
      method: "POST",
    }),
  targets: () => apiFetch<DeploymentTargetInfo[]>("/api/v1/deployment-targets"),
  templates: (appType: AppType) =>
    apiFetch<ConfigTemplateInfo[]>(`/api/v1/config-templates?appType=${appType}`),
};
