"use client";

import { useState, type ReactElement } from "react";
import { toast } from "sonner";
import { CopyButton } from "@/components/copy-button";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Skeleton } from "@/components/ui/skeleton";
import { useCommitPipeline, usePipelinePreview } from "@/hooks/use-pipelines";
import { errorMessage } from "@/lib/api/errors";
import type { Environment } from "@/lib/api/types";
import { ENVIRONMENT_LABELS } from "@/lib/environments";

interface PipelineSetupDialogProps {
  environment: Environment;
  trigger: ReactElement;
}

/**
 * Shows the generated workflow for review. Nothing is written to the repository until the user
 * approves with "Commit workflow".
 */
export function PipelineSetupDialog({ environment, trigger }: PipelineSetupDialogProps) {
  const [open, setOpen] = useState(false);
  const preview = usePipelinePreview();
  const commit = useCommitPipeline(environment.projectId);
  const label = ENVIRONMENT_LABELS[environment.type];

  function handleOpenChange(next: boolean) {
    setOpen(next);
    if (next) {
      commit.reset();
      preview.mutate(environment.id);
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger render={trigger} />
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle>CI/CD pipeline for {label}</DialogTitle>
          <DialogDescription>
            Review the GitHub Actions workflow. It runs test → build → Docker build &amp; push →
            deploy → health check on every push to <code>{environment.branch}</code>.
          </DialogDescription>
        </DialogHeader>

        {preview.isPending && <Skeleton className="h-80" />}
        {preview.isError && (
          <Alert variant="destructive">
            <AlertDescription>{errorMessage(preview.error)}</AlertDescription>
          </Alert>
        )}
        {preview.data && (
          <div className="grid min-w-0 gap-4">
            <div className="flex items-center justify-between gap-2 text-sm">
              <code className="truncate">{preview.data.workflowPath}</code>
              <CopyButton value={preview.data.content} label="Copy YAML" />
            </div>
            <pre className="max-h-80 overflow-auto rounded-lg bg-zinc-950 p-3 text-xs leading-5 text-zinc-100">
              {preview.data.content}
            </pre>
            {preview.data.fileExists && (
              <Alert>
                <AlertTitle>The workflow file already exists</AlertTitle>
                <AlertDescription>
                  Committing replaces it on {preview.data.branch}.
                </AlertDescription>
              </Alert>
            )}
            <div className="grid gap-2 text-sm">
              <p className="font-medium">Repository secrets the workflow needs</p>
              <ul className="grid gap-2">
                {preview.data.secrets.map((secret) => (
                  <li key={secret.name} className="rounded-lg border p-3">
                    <div className="flex items-center justify-between gap-2">
                      <code className="font-medium">{secret.name}</code>
                      {secret.value && <CopyButton value={secret.value} label="Copy value" />}
                    </div>
                    <p className="text-muted-foreground">{secret.description}</p>
                    {secret.value && <code className="text-xs">{secret.value}</code>}
                  </li>
                ))}
              </ul>
              <p className="text-xs text-muted-foreground">
                Add them in GitHub under Settings → Secrets and variables → Actions. Create the
                deploy token in this environment&apos;s Settings tab.
              </p>
            </div>
          </div>
        )}
        {commit.isError && <p className="text-sm text-destructive">{errorMessage(commit.error)}</p>}
        <DialogFooter>
          <Button
            disabled={!preview.data || commit.isPending}
            onClick={() =>
              commit.mutate(environment.id, {
                onSuccess: () => {
                  toast.success(`Workflow committed to ${environment.branch}`);
                  setOpen(false);
                },
              })
            }
          >
            {commit.isPending ? "Committing…" : "Commit workflow"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
