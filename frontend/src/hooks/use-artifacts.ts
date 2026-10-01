"use client";

import { useQuery } from "@tanstack/react-query";
import { artifactsApi } from "@/lib/api/artifacts";
import { queryKeys } from "@/lib/query-keys";

/**
 * Artifacts of a project, or of one deployment. While a deployment runs its build artifacts and
 * archived log are still being written, so the list refreshes.
 */
export function useArtifacts(projectId: string, deploymentId?: string, live = false) {
  return useQuery({
    queryKey: queryKeys.projects.artifacts(projectId, deploymentId),
    queryFn: () => artifactsApi.list(projectId, deploymentId),
    refetchInterval: live ? 5000 : false,
  });
}
