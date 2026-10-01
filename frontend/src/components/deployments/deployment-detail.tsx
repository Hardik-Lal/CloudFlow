"use client";

import { ArtifactList } from "@/components/artifacts/artifact-list";
import { ExternalLinkIcon } from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";
import { toast } from "sonner";
import { DeploymentAnalysis } from "@/components/assistant/deployment-analysis";
import { PageHeader } from "@/components/page-header";
import { QueryState } from "@/components/query-state";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { useCancelDeployment, useDeployment, useDeploymentLogs } from "@/hooks/use-deployments";
import { useEnvironment } from "@/hooks/use-environments";
import { useProject } from "@/hooks/use-projects";
import { errorMessage } from "@/lib/api/errors";
import type { Deployment } from "@/lib/api/types";
import { formatDuration, isInProgress, shortSha } from "@/lib/deployments";
import { deploymentTriggerPermission, ENVIRONMENT_LABELS } from "@/lib/environments";
import { formatDateTime } from "@/lib/format";
import { DeploymentStatusBadge } from "./deployment-status-badge";
import { DeploymentSteps } from "./deployment-steps";
import { LogViewer } from "./log-viewer";

export function DeploymentDetail({ deploymentId }: { deploymentId: string }) {
  const { data: deployment, isPending, error } = useDeployment(deploymentId);
  return (
    <QueryState
      isPending={isPending}
      error={error}
      errorTitle="Deployment unavailable"
      skeletonClassName="h-64"
    >
      {() => deployment && <DeploymentView deployment={deployment} />}
    </QueryState>
  );
}

function DeploymentView({ deployment }: { deployment: Deployment }) {
  const live = isInProgress(deployment.status);
  const logs = useDeploymentLogs(deployment.id, live);
  const environment = useEnvironment(deployment.environmentId).data;
  const project = useProject(deployment.projectId).data;
  const cancel = useCancelDeployment();
  const canTrigger =
    !!environment && !!project?.permissions.includes(deploymentTriggerPermission(environment.type));

  const details: [string, ReactNode][] = [
    ["Branch", deployment.branch],
    [
      "Commit",
      deployment.commitSha ? (
        <span>
          <code className="text-xs">{shortSha(deployment.commitSha)}</code>{" "}
          {deployment.commitMessage}
        </span>
      ) : (
        "resolving…"
      ),
    ],
    [
      "Trigger",
      deployment.pipelineRunId && project ? (
        <a
          href={`https://github.com/${project.repository.fullName}/actions/runs/${deployment.pipelineRunId}`}
          target="_blank"
          rel="noreferrer"
          className="hover:underline"
        >
          pipeline run {deployment.pipelineRunId}
        </a>
      ) : (
        deployment.triggerType.toLowerCase()
      ),
    ],
    [
      "Image",
      deployment.imageTag ? <code className="text-xs break-all">{deployment.imageTag}</code> : "—",
    ],
    ["Target", deployment.target === "KUBERNETES" ? "Kubernetes" : "Docker"],
    [
      deployment.target === "KUBERNETES" ? "Workload" : "Container",
      deployment.containerName ?? "—",
    ],
    ["Vulnerability scan", scanSummary(deployment)],
    ["Started", formatDateTime(deployment.startedAt)],
    ["Duration", formatDuration(deployment.startedAt, deployment.finishedAt)],
  ];

  return (
    <div className="space-y-6">
      <div className="text-sm text-muted-foreground">
        <Link href={`/environments/${deployment.environmentId}`} className="hover:text-foreground">
          ← {project?.name ?? "Project"}
          {environment && ` · ${ENVIRONMENT_LABELS[environment.type]}`}
        </Link>
      </div>
      <PageHeader
        title={
          <span className="flex items-center gap-3">
            Deployment {deployment.id.slice(0, 8)}
            <DeploymentStatusBadge status={deployment.status} />
          </span>
        }
        description={deployment.rollbackOfId ? "Rollback to an earlier image" : undefined}
        actions={
          <>
            {deployment.url && (
              <a
                href={deployment.url}
                target="_blank"
                rel="noreferrer"
                className="inline-flex items-center gap-1 text-sm font-medium hover:underline"
              >
                {deployment.url} <ExternalLinkIcon className="size-3.5" />
              </a>
            )}
            {canTrigger && (deployment.status === "QUEUED" || deployment.status === "BUILDING") && (
              <Button
                variant="outline"
                onClick={() =>
                  cancel.mutate(deployment.id, {
                    onSuccess: () => toast.success("Deployment cancelled"),
                    onError: (e) => toast.error(errorMessage(e)),
                  })
                }
              >
                Cancel
              </Button>
            )}
          </>
        }
      />
      <DeploymentSteps deployment={deployment} />
      {deployment.failureReason && deployment.status === "FAILED" && (
        <Alert variant="destructive">
          <AlertTitle>Deployment failed</AlertTitle>
          <AlertDescription>
            {deployment.failureReason}. The previous deployment, if any, keeps serving.
          </AlertDescription>
        </Alert>
      )}
      {!live && project?.permissions.includes("AI_USE") && (
        <DeploymentAnalysis deploymentId={deployment.id} failed={deployment.status === "FAILED"} />
      )}
      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Logs</CardTitle>
          </CardHeader>
          <CardContent>
            <LogViewer lines={logs.lines} error={logs.error} live={live} />
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle>Details</CardTitle>
          </CardHeader>
          <CardContent>
            <dl className="grid gap-3 text-sm">
              {details.map(([label, value]) => (
                <div key={label}>
                  <dt className="text-muted-foreground">{label}</dt>
                  <dd>{value}</dd>
                </div>
              ))}
            </dl>
          </CardContent>
        </Card>
      </div>
      <Card>
        <CardHeader>
          <CardTitle>Artifacts</CardTitle>
        </CardHeader>
        <CardContent>
          <ArtifactList projectId={deployment.projectId} deploymentId={deployment.id} live={live} />
        </CardContent>
      </Card>
    </div>
  );
}

function scanSummary(deployment: Deployment): string {
  switch (deployment.scanStatus) {
    case "PASSED":
      return "No HIGH or CRITICAL vulnerabilities";
    case "VULNERABLE":
    case "BLOCKED":
      return `${deployment.vulnerabilitiesCritical ?? 0} critical, ${deployment.vulnerabilitiesHigh ?? 0} high${deployment.scanStatus === "BLOCKED" ? " (blocked)" : ""}`;
    case "ERROR":
      return "Scanner could not run";
    case "SKIPPED":
      return "Not scanned";
    default:
      return "—";
  }
}
