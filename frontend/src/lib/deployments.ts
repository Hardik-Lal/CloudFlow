import { IN_PROGRESS_STATUSES, type Deployment, type DeploymentStatus } from "@/lib/api/types";

export const STATUS_LABELS: Record<DeploymentStatus, string> = {
  QUEUED: "Queued",
  BUILDING: "Building",
  DEPLOYING: "Deploying",
  HEALTH_CHECK: "Health check",
  SUCCEEDED: "Succeeded",
  FAILED: "Failed",
  CANCELLED: "Cancelled",
  ROLLED_BACK: "Rolled back",
};

export function isInProgress(status: DeploymentStatus): boolean {
  return IN_PROGRESS_STATUSES.includes(status);
}

/** Polling interval for data that changes while a deployment runs (false when idle). */
export function pollWhileInProgress(deployments: Deployment[] | undefined): number | false {
  return deployments?.some((deployment) => isInProgress(deployment.status)) ? 2000 : false;
}

export function formatDuration(startIso: string | null, endIso: string | null): string {
  if (!startIso) {
    return "—";
  }
  const end = endIso ? new Date(endIso).getTime() : Date.now();
  const seconds = Math.max(0, Math.round((end - new Date(startIso).getTime()) / 1000));
  return seconds < 60 ? `${seconds}s` : `${Math.floor(seconds / 60)}m ${seconds % 60}s`;
}

export function shortSha(sha: string | null): string {
  return sha ? sha.slice(0, 7) : "—";
}
