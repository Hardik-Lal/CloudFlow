"use client";

import { RocketIcon } from "lucide-react";
import { useRouter } from "next/navigation";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { useTriggerDeployment } from "@/hooks/use-deployments";
import { ApiError, errorMessage } from "@/lib/api/errors";
import type { Environment } from "@/lib/api/types";
import { ENVIRONMENT_LABELS } from "@/lib/environments";

export function DeployButton({ environment }: { environment: Environment }) {
  const trigger = useTriggerDeployment(environment.id);
  const router = useRouter();

  return (
    <Button
      disabled={trigger.isPending}
      onClick={() =>
        trigger.mutate(undefined, {
          onSuccess: (deployment) => {
            toast.success(
              `Deploying ${environment.branch} to ${ENVIRONMENT_LABELS[environment.type]}`,
            );
            router.push(`/deployments/${deployment.id}`);
          },
          onError: (error) =>
            toast.error(
              error instanceof ApiError && error.status === 422
                ? `Fix the configuration first: ${errorMessage(error)}`
                : errorMessage(error),
            ),
        })
      }
    >
      <RocketIcon />
      {trigger.isPending ? "Starting…" : "Deploy"}
    </Button>
  );
}
