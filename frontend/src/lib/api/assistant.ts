import { apiFetch } from "./client";
import type {
  AssistantAnswer,
  KnowledgeStatus,
  ReindexResult,
  Suggestion,
  SuggestionType,
} from "./types";

export const assistantApi = {
  ask: (projectId: string, question: string, environmentId?: string) =>
    apiFetch<AssistantAnswer>(`/api/v1/projects/${projectId}/assistant/query`, {
      method: "POST",
      body: { question, environmentId },
    }),
  analyzeDeployment: (deploymentId: string) =>
    apiFetch<AssistantAnswer>(`/api/v1/deployments/${deploymentId}/analysis`, { method: "POST" }),
  knowledge: (projectId: string) =>
    apiFetch<KnowledgeStatus>(`/api/v1/projects/${projectId}/knowledge`),
  reindex: (projectId: string) =>
    apiFetch<ReindexResult>(`/api/v1/projects/${projectId}/knowledge/reindex`, { method: "POST" }),
  suggestions: (projectId: string) =>
    apiFetch<Suggestion[]>(`/api/v1/projects/${projectId}/suggestions`),
  generate: (
    projectId: string,
    type: SuggestionType,
    environmentId?: string,
    instructions?: string,
  ) =>
    apiFetch<Suggestion>(`/api/v1/projects/${projectId}/suggestions`, {
      method: "POST",
      body: { type, environmentId, instructions },
    }),
  apply: (suggestionId: string) =>
    apiFetch<Suggestion>(`/api/v1/suggestions/${suggestionId}/apply`, { method: "POST" }),
  reject: (suggestionId: string) =>
    apiFetch<Suggestion>(`/api/v1/suggestions/${suggestionId}/reject`, { method: "POST" }),
};
