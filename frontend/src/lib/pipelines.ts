import type { PipelineRun } from "@/lib/api/types";

export type RunOutcome = "running" | "success" | "failure" | "cancelled" | "skipped" | "unknown";

/** Collapses GitHub's status + conclusion pair into one outcome for display. */
export function runOutcome(run: Pick<PipelineRun, "status" | "conclusion">): RunOutcome {
  if (run.status !== "completed") {
    return "running";
  }
  switch (run.conclusion) {
    case "success":
      return "success";
    case "failure":
    case "timed_out":
    case "startup_failure":
      return "failure";
    case "cancelled":
      return "cancelled";
    case "skipped":
    case "neutral":
      return "skipped";
    default:
      return "unknown";
  }
}

export function isRunActive(run: PipelineRun | null | undefined): boolean {
  return !!run && run.status !== "completed";
}
