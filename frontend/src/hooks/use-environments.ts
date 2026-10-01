"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { environmentsApi } from "@/lib/api/environments";
import type { AppType, DeploymentConfigInput, EnvironmentType } from "@/lib/api/types";
import { queryKeys } from "@/lib/query-keys";

export function useEnvironments(projectId: string) {
  return useQuery({
    queryKey: queryKeys.projects.environments(projectId),
    queryFn: () => environmentsApi.list(projectId),
  });
}

export function useEnvironment(environmentId: string) {
  return useQuery({
    queryKey: queryKeys.environments.detail(environmentId),
    queryFn: () => environmentsApi.get(environmentId),
  });
}

export function useVariables(environmentId: string) {
  return useQuery({
    queryKey: queryKeys.environments.variables(environmentId),
    queryFn: () => environmentsApi.variables(environmentId),
  });
}

export function useDeploymentConfig(environmentId: string) {
  return useQuery({
    queryKey: queryKeys.environments.config(environmentId),
    queryFn: () => environmentsApi.config(environmentId),
  });
}

export function useConfigValidation(environmentId: string) {
  return useQuery({
    queryKey: queryKeys.environments.validation(environmentId),
    queryFn: () => environmentsApi.validate(environmentId),
  });
}

export function useDeploymentTargets() {
  return useQuery({
    queryKey: queryKeys.deploymentTargets,
    queryFn: () => environmentsApi.targets(),
    staleTime: Infinity,
  });
}

export function useConfigTemplates(appType: AppType) {
  return useQuery({
    queryKey: queryKeys.configTemplates(appType),
    queryFn: () => environmentsApi.templates(appType),
    staleTime: Infinity,
  });
}

/** Invalidates everything derived from an environment, including the project's list. */
function useInvalidateEnvironment(environmentId: string, projectId: string) {
  const queryClient = useQueryClient();
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ["environments", environmentId] }),
      queryClient.invalidateQueries({ queryKey: queryKeys.projects.environments(projectId) }),
    ]);
}

export function useCreateEnvironment(projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ type, branch }: { type: EnvironmentType; branch?: string }) =>
      environmentsApi.create(projectId, type, branch),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: queryKeys.projects.environments(projectId) }),
  });
}

export function useUpdateEnvironmentBranch(environmentId: string, projectId: string) {
  const invalidate = useInvalidateEnvironment(environmentId, projectId);
  return useMutation({
    mutationFn: (branch: string) => environmentsApi.update(environmentId, branch),
    onSuccess: invalidate,
  });
}

export function useDeleteEnvironment(environmentId: string, projectId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => environmentsApi.remove(environmentId),
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: ["environments", environmentId] });
      return queryClient.invalidateQueries({
        queryKey: queryKeys.projects.environments(projectId),
      });
    },
  });
}

export function usePutVariable(environmentId: string, projectId: string) {
  const invalidate = useInvalidateEnvironment(environmentId, projectId);
  return useMutation({
    mutationFn: ({ key, value, secret }: { key: string; value: string; secret: boolean }) =>
      environmentsApi.putVariable(environmentId, key, value, secret),
    onSuccess: invalidate,
  });
}

export function useDeleteVariable(environmentId: string, projectId: string) {
  const invalidate = useInvalidateEnvironment(environmentId, projectId);
  return useMutation({
    mutationFn: (key: string) => environmentsApi.deleteVariable(environmentId, key),
    onSuccess: invalidate,
  });
}

export function useUpdateDeploymentConfig(environmentId: string, projectId: string) {
  const invalidate = useInvalidateEnvironment(environmentId, projectId);
  return useMutation({
    mutationFn: (config: DeploymentConfigInput) =>
      environmentsApi.updateConfig(environmentId, config),
    onSuccess: invalidate,
  });
}
