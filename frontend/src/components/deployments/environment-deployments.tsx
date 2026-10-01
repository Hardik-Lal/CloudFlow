"use client";

import { QueryState } from "@/components/query-state";
import { useEnvironmentDeployments } from "@/hooks/use-deployments";
import { DeploymentTable } from "./deployment-table";

export function EnvironmentDeployments({
  environmentId,
  canTrigger,
}: {
  environmentId: string;
  canTrigger: boolean;
}) {
  const { data, isPending, error } = useEnvironmentDeployments(environmentId);
  return (
    <QueryState isPending={isPending} error={error} errorTitle="Could not load deployments">
      {() => data && <DeploymentTable deployments={data.content} canTrigger={() => canTrigger} />}
    </QueryState>
  );
}
