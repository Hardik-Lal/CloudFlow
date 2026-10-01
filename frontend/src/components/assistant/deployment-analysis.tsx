"use client";

import { SparklesIcon } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useAnalyzeDeployment } from "@/hooks/use-assistant";
import { errorMessage } from "@/lib/api/errors";
import { AnswerCard } from "./answer-card";

/** "Analyze with AI" for one deployment: explains the failure from its own evidence. */
export function DeploymentAnalysis({
  deploymentId,
  failed,
}: {
  deploymentId: string;
  failed: boolean;
}) {
  const analyze = useAnalyzeDeployment();

  return (
    <div className="space-y-3">
      <Button
        variant={failed ? "default" : "outline"}
        disabled={analyze.isPending}
        onClick={() =>
          analyze.mutate(deploymentId, { onError: (error) => toast.error(errorMessage(error)) })
        }
      >
        <SparklesIcon />
        {analyze.isPending ? "Analyzing…" : failed ? "Explain this failure" : "Analyze with AI"}
      </Button>
      {analyze.isPending && <Skeleton className="h-40" />}
      {analyze.data && (
        <AnswerCard
          question={failed ? "Why did this deployment fail?" : "Deployment analysis"}
          answer={analyze.data}
        />
      )}
    </div>
  );
}
