"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { deployTokensApi, pipelinesApi } from "@/lib/api/pipelines";
import { isRunActive } from "@/lib/pipelines";
import { queryKeys } from "@/lib/query-keys";

export function usePipelines(projectId: string) {
  return useQuery({
    queryKey: queryKeys.projects.pipelines(projectId),
    queryFn: () => pipelinesApi.list(projectId),
    refetchInterval: (query) =>
      query.state.data?.some((pipeline) => isRunActive(pipeline.latestRun)) ? 10_000 : false,
  });
}

export function usePipeline(pipelineId: string) {
  return useQuery({
    queryKey: queryKeys.pipelines.detail(pipelineId),
    queryFn: () => pipelinesApi.get(pipelineId),
  });
}

export function usePipelineRuns(pipelineId: string) {
  return useQuery({
    queryKey: queryKeys.pipelines.runs(pipelineId),
    queryFn: () => pipelinesApi.runs(pipelineId),
  });
}

export function usePipelinePreview() {
  return useMutation({
    mutationFn: (environmentId: string) => pipelinesApi.preview(environmentId),
  });
}

export function useCommitPipeline(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (environmentId: string) => pipelinesApi.commit(environmentId),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: queryKeys.projects.pipelines(projectId) }),
  });
}

/** Pulls the latest runs from GitHub Actions and refreshes every pipeline view. */
export function useSyncPipeline(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (pipelineId: string) => pipelinesApi.sync(pipelineId),
    onSuccess: (pipeline) =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: ["pipelines", pipeline.id] }),
        queryClient.invalidateQueries({ queryKey: queryKeys.projects.pipelines(projectId) }),
      ]),
  });
}

export function useDeployTokens(environmentId: string) {
  return useQuery({
    queryKey: queryKeys.environments.deployTokens(environmentId),
    queryFn: () => deployTokensApi.list(environmentId),
  });
}

export function useCreateDeployToken(environmentId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (name: string) => deployTokensApi.create(environmentId, name),
    onSuccess: () =>
      queryClient.invalidateQueries({
        queryKey: queryKeys.environments.deployTokens(environmentId),
      }),
  });
}

export function useRevokeDeployToken(environmentId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (tokenId: string) => deployTokensApi.revoke(tokenId),
    onSuccess: () =>
      queryClient.invalidateQueries({
        queryKey: queryKeys.environments.deployTokens(environmentId),
      }),
  });
}
