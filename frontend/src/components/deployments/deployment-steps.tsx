import { CheckIcon, CircleIcon, LoaderCircleIcon, XIcon } from "lucide-react";
import type { Deployment } from "@/lib/api/types";
import { cn } from "@/lib/utils";

const STEPS = ["Queued", "Build", "Deploy", "Health check", "Live"] as const;

const STEP_OF_STATUS: Partial<Record<Deployment["status"], number>> = {
  QUEUED: 0,
  BUILDING: 1,
  DEPLOYING: 2,
  HEALTH_CHECK: 3,
  SUCCEEDED: 4,
  ROLLED_BACK: 4,
};

/** Index of the step a finished-but-unsuccessful deployment stopped at, from what it recorded. */
function stoppedAt(deployment: Deployment): number {
  if (deployment.containerName) {
    return 3;
  }
  if (deployment.imageTag && deployment.triggerType !== "ROLLBACK") {
    return 2;
  }
  return deployment.startedAt ? 1 : 0;
}

export function DeploymentSteps({ deployment }: { deployment: Deployment }) {
  const failed = deployment.status === "FAILED" || deployment.status === "CANCELLED";
  const position = failed ? stoppedAt(deployment) : (STEP_OF_STATUS[deployment.status] ?? 0);
  const finished = deployment.status === "SUCCEEDED" || deployment.status === "ROLLED_BACK";

  return (
    <ol className="flex flex-wrap items-center gap-2 text-sm">
      {STEPS.map((label, index) => {
        const done = index < position || (finished && index === position);
        const current = !failed && !finished && index === position;
        const stopped = failed && index === position;
        return (
          <li key={label} className="flex items-center gap-2">
            <span
              className={cn(
                "flex size-6 items-center justify-center rounded-full border",
                done && "border-emerald-600 bg-emerald-600 text-white",
                current && "border-foreground",
                stopped && "border-destructive",
              )}
            >
              {done ? (
                <CheckIcon className="size-3.5" />
              ) : current ? (
                <LoaderCircleIcon className="size-3.5 animate-spin" />
              ) : stopped ? (
                <XIcon className="size-3.5 text-destructive" />
              ) : (
                <CircleIcon className="size-2 text-muted-foreground" />
              )}
            </span>
            <span className={cn(!done && !current && !stopped && "text-muted-foreground")}>
              {label}
            </span>
            {index < STEPS.length - 1 && <span className="h-px w-6 bg-border" />}
          </li>
        );
      })}
    </ol>
  );
}
