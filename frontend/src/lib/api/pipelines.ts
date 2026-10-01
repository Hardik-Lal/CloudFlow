import { apiFetch } from "./client";
import type {
  CreatedDeployToken,
  DeployToken,
  Page,
  Pipeline,
  PipelinePreview,
  PipelineRun,
} from "./types";

export const pipelinesApi = {
  list: (projectId: string) => apiFetch<Pipeline[]>(`/api/v1/projects/${projectId}/pipelines`),
  get: (pipelineId: string) => apiFetch<Pipeline>(`/api/v1/pipelines/${pipelineId}`),
  runs: (pipelineId: string, page = 0, size = 20) =>
    apiFetch<Page<PipelineRun>>(`/api/v1/pipelines/${pipelineId}/runs?page=${page}&size=${size}`),
  preview: (environmentId: string) =>
    apiFetch<PipelinePreview>(`/api/v1/environments/${environmentId}/pipeline/preview`, {
      method: "POST",
    }),
  commit: (environmentId: string) =>
    apiFetch<Pipeline>(`/api/v1/environments/${environmentId}/pipeline`, { method: "POST" }),
  sync: (pipelineId: string) =>
    apiFetch<Pipeline>(`/api/v1/pipelines/${pipelineId}/sync`, { method: "POST" }),
  remove: (pipelineId: string) =>
    apiFetch<void>(`/api/v1/pipelines/${pipelineId}`, { method: "DELETE" }),
};

export const deployTokensApi = {
  list: (environmentId: string) =>
    apiFetch<DeployToken[]>(`/api/v1/environments/${environmentId}/deploy-tokens`),
  create: (environmentId: string, name: string) =>
    apiFetch<CreatedDeployToken>(`/api/v1/environments/${environmentId}/deploy-tokens`, {
      method: "POST",
      body: { name },
    }),
  revoke: (tokenId: string) =>
    apiFetch<void>(`/api/v1/deploy-tokens/${tokenId}`, { method: "DELETE" }),
};
