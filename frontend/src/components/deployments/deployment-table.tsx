"use client";

import { ExternalLinkIcon } from "lucide-react";
import Link from "next/link";
import { toast } from "sonner";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { useCancelDeployment, useRollbackDeployment } from "@/hooks/use-deployments";
import { errorMessage } from "@/lib/api/errors";
import type { Deployment, EnvironmentType } from "@/lib/api/types";
import { formatDuration, shortSha } from "@/lib/deployments";
import { ENVIRONMENT_LABELS } from "@/lib/environments";
import { formatRelativeTime } from "@/lib/format";
import { DeploymentStatusBadge } from "./deployment-status-badge";

interface DeploymentTableProps {
  deployments: Deployment[];
  /** Environment type per environment id; adds an Environment column when provided. */
  environmentTypes?: Record<string, EnvironmentType>;
  /** Whether the caller may roll back / cancel deployments of the given environment. */
  canTrigger: (environmentId: string) => boolean;
}

export function DeploymentTable({
  deployments,
  environmentTypes,
  canTrigger,
}: DeploymentTableProps) {
  const rollback = useRollbackDeployment();
  const cancel = useCancelDeployment();

  if (deployments.length === 0) {
    return (
      <div className="rounded-xl border border-dashed p-10 text-center text-sm text-muted-foreground">
        No deployments yet.
      </div>
    );
  }

  return (
    <div className="rounded-xl border">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Status</TableHead>
            {environmentTypes && <TableHead>Environment</TableHead>}
            <TableHead>Commit</TableHead>
            <TableHead>Trigger</TableHead>
            <TableHead>Started</TableHead>
            <TableHead>Duration</TableHead>
            <TableHead className="w-32" />
          </TableRow>
        </TableHeader>
        <TableBody>
          {deployments.map((deployment) => {
            const type = environmentTypes?.[deployment.environmentId];
            const allowed = canTrigger(deployment.environmentId);
            const canRollback =
              allowed &&
              !deployment.active &&
              (deployment.status === "SUCCEEDED" || deployment.status === "ROLLED_BACK");
            const canCancel =
              allowed && (deployment.status === "QUEUED" || deployment.status === "BUILDING");
            return (
              <TableRow key={deployment.id}>
                <TableCell>
                  <div className="flex items-center gap-2">
                    <DeploymentStatusBadge status={deployment.status} />
                    {deployment.active && <Badge variant="secondary">Live</Badge>}
                  </div>
                </TableCell>
                {environmentTypes && <TableCell>{type ? ENVIRONMENT_LABELS[type] : "—"}</TableCell>}
                <TableCell className="max-w-72">
                  <Link href={`/deployments/${deployment.id}`} className="hover:underline">
                    <code className="text-xs">{shortSha(deployment.commitSha)}</code>{" "}
                    <span className="line-clamp-1 inline text-sm">
                      {deployment.commitMessage ?? deployment.branch}
                    </span>
                  </Link>
                </TableCell>
                <TableCell className="text-sm text-muted-foreground">
                  {deployment.triggerType.toLowerCase()}
                </TableCell>
                <TableCell className="text-sm text-muted-foreground">
                  {formatRelativeTime(deployment.createdAt)}
                </TableCell>
                <TableCell className="text-sm text-muted-foreground">
                  {formatDuration(deployment.startedAt, deployment.finishedAt)}
                </TableCell>
                <TableCell className="text-right">
                  <div className="flex justify-end gap-1">
                    {deployment.url && (
                      <a
                        href={deployment.url}
                        target="_blank"
                        rel="noreferrer"
                        className="inline-flex items-center gap-1 px-2 text-sm hover:underline"
                      >
                        Open <ExternalLinkIcon className="size-3" />
                      </a>
                    )}
                    {canRollback && (
                      <ConfirmDialog
                        trigger={
                          <Button variant="outline" size="sm">
                            Roll back
                          </Button>
                        }
                        title="Roll back to this deployment?"
                        description={`Redeploys the image built from ${shortSha(deployment.commitSha)}. The current deployment keeps serving until the rollback is healthy.`}
                        confirmLabel="Roll back"
                        destructive={false}
                        onConfirm={() =>
                          rollback.mutate(deployment.id, {
                            onSuccess: () => toast.success("Rollback started"),
                            onError: (e) => toast.error(errorMessage(e)),
                          })
                        }
                      />
                    )}
                    {canCancel && (
                      <Button
                        variant="ghost"
                        size="sm"
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
                  </div>
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </div>
  );
}
