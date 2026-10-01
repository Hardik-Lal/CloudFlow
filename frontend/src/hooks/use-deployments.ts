"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useCallback, useEffect, useRef, useState } from "react";
import { deploymentsApi } from "@/lib/api/deployments";
import type { Deployment, DeploymentLogLine, DeploymentStatusMessage } from "@/lib/api/types";
import { isInProgress, pollWhileInProgress } from "@/lib/deployments";
import { queryKeys } from "@/lib/query-keys";
import { subscribe } from "@/lib/realtime";

export function useEnvironmentDeployments(environmentId: string, page = 0) {
  return useQuery({
    queryKey: queryKeys.environments.deployments(environmentId, page),
    queryFn: () => deploymentsApi.forEnvironment(environmentId, page),
    refetchInterval: (query) => pollWhileInProgress(query.state.data?.content),
  });
}

export function useProjectDeployments(projectId: string, page = 0) {
  return useQuery({
    queryKey: queryKeys.projects.deployments(projectId, page),
    queryFn: () => deploymentsApi.forProject(projectId, page),
    refetchInterval: (query) => pollWhileInProgress(query.state.data?.content),
  });
}

/** Deployment details, refreshed immediately when its status changes (pushed over WebSocket). */
export function useDeployment(deploymentId: string) {
  const queryClient = useQueryClient();
  const query = useQuery({
    queryKey: queryKeys.deployments.detail(deploymentId),
    queryFn: () => deploymentsApi.get(deploymentId),
    // Fallback in case the WebSocket is unavailable.
    refetchInterval: (query) =>
      query.state.data && isInProgress(query.state.data.status) ? 10_000 : false,
  });
  useEffect(
    () =>
      subscribe<DeploymentStatusMessage>(`/topic/deployments/${deploymentId}/status`, () =>
        queryClient.invalidateQueries({ queryKey: queryKeys.deployments.detail(deploymentId) }),
      ),
    [deploymentId, queryClient],
  );
  return query;
}

/** Refreshes every deployment list and detail after a deployment action. */
function useInvalidateDeployments() {
  const queryClient = useQueryClient();
  return (deployment: Deployment) =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ["environments", deployment.environmentId] }),
      queryClient.invalidateQueries({
        queryKey: ["projects", deployment.projectId, "deployments"],
      }),
      queryClient.invalidateQueries({ queryKey: ["deployments"] }),
    ]);
}

export function useTriggerDeployment(environmentId: string) {
  const invalidate = useInvalidateDeployments();
  return useMutation({
    mutationFn: (commitSha?: string) => deploymentsApi.trigger(environmentId, commitSha),
    onSuccess: invalidate,
  });
}

export function useRollbackDeployment() {
  const invalidate = useInvalidateDeployments();
  return useMutation({
    mutationFn: (deploymentId: string) => deploymentsApi.rollback(deploymentId),
    onSuccess: invalidate,
  });
}

export function useCancelDeployment() {
  const invalidate = useInvalidateDeployments();
  return useMutation({
    mutationFn: (deploymentId: string) => deploymentsApi.cancel(deploymentId),
    onSuccess: invalidate,
  });
}

/**
 * Deployment log lines: stored lines over REST, then new lines pushed over WebSocket while the
 * deployment runs. When it finishes, one more REST fetch picks up anything missed.
 */
export function useDeploymentLogs(deploymentId: string, live: boolean) {
  const [lines, setLines] = useState<DeploymentLogLine[]>([]);
  const [error, setError] = useState<unknown>(null);
  const lastId = useRef(0);

  const appendNew = useCallback((next: DeploymentLogLine[]) => {
    const fresh = next.filter((line) => line.id > lastId.current);
    if (fresh.length > 0) {
      lastId.current = fresh[fresh.length - 1].id;
      setLines((current) => [...current, ...fresh]);
    }
  }, []);

  const fetchRemaining = useCallback(async () => {
    // Pages of up to 1000 lines until caught up.
    for (;;) {
      const next = await deploymentsApi.logs(deploymentId, lastId.current);
      appendNew(next);
      if (next.length < 1000) {
        return;
      }
    }
  }, [deploymentId, appendNew]);

  useEffect(() => {
    let cancelled = false;
    fetchRemaining().catch((e) => !cancelled && setError(e));
    const unsubscribe = live
      ? subscribe<DeploymentLogLine>(`/topic/deployments/${deploymentId}/logs`, (line) =>
          appendNew([line]),
        )
      : () => undefined;
    return () => {
      cancelled = true;
      unsubscribe();
    };
  }, [deploymentId, live, fetchRemaining, appendNew]);

  return { lines, error };
}
