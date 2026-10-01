"use client";

import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { githubApi } from "@/lib/api/github";
import { projectsApi, type CreateProjectInput, type UpdateProjectInput } from "@/lib/api/projects";
import { queryKeys } from "@/lib/query-keys";

export function useProjects(organizationId: string) {
  return useQuery({
    queryKey: queryKeys.organizations.projects(organizationId),
    queryFn: () => projectsApi.list(organizationId),
  });
}

export function useProject(projectId: string) {
  return useQuery({
    queryKey: queryKeys.projects.detail(projectId),
    queryFn: () => projectsApi.get(projectId),
  });
}

export function useBranches(projectId: string) {
  return useQuery({
    queryKey: queryKeys.projects.branches(projectId),
    queryFn: () => projectsApi.branches(projectId),
  });
}

export function useCommits(projectId: string, branch: string | undefined) {
  return useQuery({
    queryKey: queryKeys.projects.commits(projectId, branch),
    queryFn: () => projectsApi.commits(projectId, branch),
  });
}

export function usePullRequests(projectId: string) {
  return useQuery({
    queryKey: queryKeys.projects.pulls(projectId),
    queryFn: () => projectsApi.pulls(projectId),
  });
}

/** The caller's GitHub repositories, fetched page by page. */
export function useGithubRepositories() {
  return useInfiniteQuery({
    queryKey: queryKeys.github.repositories,
    queryFn: ({ pageParam }) => githubApi.repositories(pageParam),
    initialPageParam: 1,
    getNextPageParam: (lastPage) => (lastPage.hasNext ? lastPage.page + 1 : undefined),
    staleTime: 60_000,
  });
}

export function useCreateProject(organizationId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: CreateProjectInput) => projectsApi.create(organizationId, input),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: queryKeys.organizations.projects(organizationId) }),
  });
}

function useProjectMutationSuccess(projectId: string) {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: ["projects", projectId] });
}

export function useUpdateProject(projectId: string) {
  const onSuccess = useProjectMutationSuccess(projectId);
  return useMutation({
    mutationFn: (input: UpdateProjectInput) => projectsApi.update(projectId, input),
    onSuccess,
  });
}

export function useSyncProject(projectId: string) {
  const onSuccess = useProjectMutationSuccess(projectId);
  return useMutation({ mutationFn: () => projectsApi.sync(projectId), onSuccess });
}

export function useDeleteProject(projectId: string, organizationId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => projectsApi.remove(projectId),
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: ["projects", projectId] });
      return queryClient.invalidateQueries({
        queryKey: queryKeys.organizations.projects(organizationId),
      });
    },
  });
}
