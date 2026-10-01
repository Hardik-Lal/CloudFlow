"use client";

import { ExternalLinkIcon, RefreshCwIcon } from "lucide-react";
import Link from "next/link";
import { useEffect, useRef } from "react";
import { toast } from "sonner";
import { PageHeader } from "@/components/page-header";
import { QueryState } from "@/components/query-state";
import { Button, buttonVariants } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { usePipeline, usePipelineRuns, useSyncPipeline } from "@/hooks/use-pipelines";
import { errorMessage } from "@/lib/api/errors";
import type { Pipeline } from "@/lib/api/types";
import { formatDuration } from "@/lib/deployments";
import { ENVIRONMENT_LABELS } from "@/lib/environments";
import { formatRelativeTime } from "@/lib/format";
import { isRunActive } from "@/lib/pipelines";
import { RunOutcomeBadge } from "./run-outcome-badge";

export function PipelineDetail({ pipelineId }: { pipelineId: string }) {
  const { data: pipeline, isPending, error } = usePipeline(pipelineId);
  return (
    <QueryState
      isPending={isPending}
      error={error}
      errorTitle="Pipeline unavailable"
      skeletonClassName="h-64"
    >
      {() => pipeline && <PipelineView pipeline={pipeline} />}
    </QueryState>
  );
}

function PipelineView({ pipeline }: { pipeline: Pipeline }) {
  const runs = usePipelineRuns(pipeline.id);
  const sync = useSyncPipeline(pipeline.projectId);
  const { mutate: syncNow } = sync;
  const active = runs.data?.content.some(isRunActive) ?? false;
  const synced = useRef(false);

  // Pull fresh data from GitHub when the page opens, then keep syncing while a run is active.
  useEffect(() => {
    if (!synced.current) {
      synced.current = true;
      syncNow(pipeline.id);
    }
    if (!active) {
      return;
    }
    const timer = setInterval(() => syncNow(pipeline.id), 10_000);
    return () => clearInterval(timer);
  }, [active, pipeline.id, syncNow]);

  return (
    <div className="space-y-6">
      <div className="text-sm text-muted-foreground">
        <Link href={`/projects/${pipeline.projectId}`} className="hover:text-foreground">
          ← Back to project
        </Link>
      </div>
      <PageHeader
        title={`${ENVIRONMENT_LABELS[pipeline.environmentType]} pipeline`}
        description={
          <span>
            <a
              href={pipeline.workflowUrl}
              target="_blank"
              rel="noreferrer"
              className="hover:underline"
            >
              {pipeline.workflowPath}
            </a>{" "}
            · runs on push to {pipeline.branch} · last synced{" "}
            {formatRelativeTime(pipeline.lastSyncedAt)}
          </span>
        }
        actions={
          <>
            <Button
              variant="outline"
              disabled={sync.isPending}
              onClick={() =>
                sync.mutate(pipeline.id, {
                  onSuccess: () => toast.success("Synced with GitHub Actions"),
                  onError: (e) => toast.error(errorMessage(e)),
                })
              }
            >
              <RefreshCwIcon className={sync.isPending ? "animate-spin" : undefined} />
              Sync
            </Button>
            <a
              href={pipeline.actionsUrl}
              target="_blank"
              rel="noreferrer"
              className={buttonVariants({ variant: "ghost" })}
            >
              GitHub Actions <ExternalLinkIcon className="size-3.5" />
            </a>
          </>
        }
      />
      <QueryState isPending={runs.isPending} error={runs.error} errorTitle="Could not load runs">
        {() =>
          runs.data && runs.data.content.length > 0 ? (
            <div className="rounded-xl border">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Run</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Commit</TableHead>
                    <TableHead>Jobs</TableHead>
                    <TableHead>Started</TableHead>
                    <TableHead>Duration</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {runs.data.content.map((run) => (
                    <TableRow key={run.id}>
                      <TableCell>
                        <a
                          href={run.htmlUrl}
                          target="_blank"
                          rel="noreferrer"
                          className="hover:underline"
                        >
                          #{run.runNumber}
                        </a>
                        <div className="text-xs text-muted-foreground">{run.event}</div>
                      </TableCell>
                      <TableCell>
                        <RunOutcomeBadge status={run.status} conclusion={run.conclusion} />
                      </TableCell>
                      <TableCell className="max-w-64">
                        <code className="text-xs">{run.headSha.slice(0, 7)}</code>{" "}
                        <span className="line-clamp-1 inline text-sm">{run.commitMessage}</span>
                        <div className="text-xs text-muted-foreground">{run.actor}</div>
                      </TableCell>
                      <TableCell>
                        <div className="flex flex-wrap gap-1">
                          {run.jobs.map((job) => (
                            <a
                              key={job.name}
                              href={job.htmlUrl ?? run.htmlUrl}
                              target="_blank"
                              rel="noreferrer"
                              title={`${job.name}: ${job.conclusion ?? job.status}`}
                            >
                              <RunOutcomeBadge status={job.status} conclusion={job.conclusion} />
                              <span className="sr-only">{job.name}</span>
                            </a>
                          ))}
                        </div>
                        <div className="text-xs text-muted-foreground">
                          {run.jobs.map((job) => job.name).join(" → ")}
                        </div>
                      </TableCell>
                      <TableCell className="text-sm text-muted-foreground">
                        {formatRelativeTime(run.startedAt)}
                      </TableCell>
                      <TableCell className="text-sm text-muted-foreground">
                        {formatDuration(run.startedAt, run.completedAt)}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          ) : (
            <div className="rounded-xl border border-dashed p-10 text-center text-sm text-muted-foreground">
              No runs recorded yet. Runs appear after the workflow runs on GitHub (sync, or
              configure the GitHub webhook for live updates).
            </div>
          )
        }
      </QueryState>
    </div>
  );
}
