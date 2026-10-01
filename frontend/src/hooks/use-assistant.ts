"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { assistantApi } from "@/lib/api/assistant";
import type { SuggestionType } from "@/lib/api/types";
import { queryKeys } from "@/lib/query-keys";

export function useKnowledgeStatus(projectId: string) {
  return useQuery({
    queryKey: queryKeys.projects.knowledge(projectId),
    queryFn: () => assistantApi.knowledge(projectId),
  });
}

export function useReindexKnowledge(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => assistantApi.reindex(projectId),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: queryKeys.projects.knowledge(projectId) }),
  });
}

export function useAskAssistant(projectId: string) {
  return useMutation({
    mutationFn: ({ question, environmentId }: { question: string; environmentId?: string }) =>
      assistantApi.ask(projectId, question, environmentId),
  });
}

export function useAnalyzeDeployment() {
  return useMutation({
    mutationFn: (deploymentId: string) => assistantApi.analyzeDeployment(deploymentId),
  });
}

export function useSuggestions(projectId: string) {
  return useQuery({
    queryKey: queryKeys.projects.suggestions(projectId),
    queryFn: () => assistantApi.suggestions(projectId),
  });
}

export function useGenerateSuggestion(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: { type: SuggestionType; environmentId?: string; instructions?: string }) =>
      assistantApi.generate(projectId, input.type, input.environmentId, input.instructions),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: queryKeys.projects.suggestions(projectId) }),
  });
}

/** Applying can create variables or commits, so environment and project data are refreshed. */
export function useReviewSuggestion(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, decision }: { id: string; decision: "apply" | "reject" }) =>
      decision === "apply" ? assistantApi.apply(id) : assistantApi.reject(id),
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: ["projects", projectId] }),
        queryClient.invalidateQueries({ queryKey: ["environments"] }),
      ]),
  });
}
