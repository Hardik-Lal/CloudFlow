"use client";

import { ExternalLinkIcon, RefreshCwIcon, WorkflowIcon } from "lucide-react";
import Link from "next/link";
import { toast } from "sonner";
import { QueryState } from "@/components/query-state";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { useEnvironments } from "@/hooks/use-environments";
import { usePipelines, useSyncPipeline } from "@/hooks/use-pipelines";
import { errorMessage } from "@/lib/api/errors";
import type { Environment, Pipeline, Project } from "@/lib/api/types";
import { ENVIRONMENT_LABELS } from "@/lib/environments";
import { formatRelativeTime } from "@/lib/format";
import { PipelineSetupDialog } from "./pipeline-setup-dialog";
import { RunOutcomeBadge } from "./run-outcome-badge";

export function ProjectPipelines({ project }: { project: Project }) {
  const environments = useEnvironments(project.id);
  const pipelines = usePipelines(project.id);
  const canWrite = project.permissions.includes("PIPELINE_WRITE");

  return (
    <QueryState
      isPending={environments.isPending || pipelines.isPending}
      error={environments.error ?? pipelines.error}
      errorTitle="Could not load pipelines"
    >
      {() =>
        environments.data && environments.data.length > 0 ? (
          <div className="grid gap-4 md:grid-cols-3">
            {environments.data.map((environment) => (
              <PipelineCard
                key={environment.id}
                project={project}
                environment={environment}
                pipeline={pipelines.data?.find((p) => p.environmentId === environment.id)}
                canWrite={canWrite}
              />
            ))}
          </div>
        ) : (
          <div className="rounded-xl border border-dashed p-10 text-center text-sm text-muted-foreground">
            Create an environment first; each environment gets its own pipeline.
          </div>
        )
      }
    </QueryState>
  );
}

interface PipelineCardProps {
  project: Project;
  environment: Environment;
  pipeline: Pipeline | undefined;
  canWrite: boolean;
}

function PipelineCard({ project, environment, pipeline, canWrite }: PipelineCardProps) {
  const sync = useSyncPipeline(project.id);
  const run = pipeline?.latestRun;

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <WorkflowIcon className="size-4" />
          {ENVIRONMENT_LABELS[environment.type]}
        </CardTitle>
        <CardDescription>
          {pipeline ? (
            <a
              href={pipeline.workflowUrl}
              target="_blank"
              rel="noreferrer"
              className="hover:underline"
            >
              {pipeline.workflowPath}
            </a>
          ) : (
            "No pipeline yet"
          )}
        </CardDescription>
      </CardHeader>
      <CardContent className="grid gap-3 text-sm">
        {pipeline &&
          (run ? (
            <div className="grid gap-1">
              <div className="flex items-center gap-2">
                <RunOutcomeBadge status={run.status} conclusion={run.conclusion} />
                <a href={run.htmlUrl} target="_blank" rel="noreferrer" className="hover:underline">
                  Run #{run.runNumber}
                </a>
              </div>
              <span className="line-clamp-1 text-muted-foreground">
                {run.commitMessage ?? run.headSha.slice(0, 7)} · {formatRelativeTime(run.startedAt)}
              </span>
            </div>
          ) : (
            <span className="text-muted-foreground">
              No runs yet. Push to {environment.branch} (with the secrets configured) to start one.
            </span>
          ))}
        <div className="flex flex-wrap gap-2">
          {pipeline && (
            <Link href={`/pipelines/${pipeline.id}`} className={buttonVariants({ size: "sm" })}>
              View runs
            </Link>
          )}
          {pipeline && (
            <Button
              size="sm"
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
          )}
          {canWrite && (
            <PipelineSetupDialog
              environment={environment}
              trigger={
                <Button size="sm" variant={pipeline ? "ghost" : "default"}>
                  {pipeline ? "Regenerate" : "Set up pipeline"}
                </Button>
              }
            />
          )}
          {pipeline && (
            <a
              href={pipeline.actionsUrl}
              target="_blank"
              rel="noreferrer"
              className={buttonVariants({ size: "sm", variant: "ghost" })}
            >
              Actions <ExternalLinkIcon className="size-3" />
            </a>
          )}
        </div>
      </CardContent>
    </Card>
  );
}
