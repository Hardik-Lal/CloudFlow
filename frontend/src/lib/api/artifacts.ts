import { apiDownload, apiFetch } from "./client";
import type { Artifact, Page } from "./types";

export const artifactsApi = {
  list: (projectId: string, deploymentId?: string) => {
    const query = new URLSearchParams({ size: "100" });
    if (deploymentId) {
      query.set("deploymentId", deploymentId);
    }
    return apiFetch<Page<Artifact>>(`/api/v1/projects/${projectId}/artifacts?${query}`);
  },
  download: (artifactId: string) => apiDownload(`/api/v1/artifacts/${artifactId}/content`),
};
