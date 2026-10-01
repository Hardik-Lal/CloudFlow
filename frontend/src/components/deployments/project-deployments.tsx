"use client";

import { QueryState } from "@/components/query-state";
import { useProjectDeployments } from "@/hooks/use-deployments";
import { useEnvironments } from "@/hooks/use-environments";
import type { EnvironmentType, Project } from "@/lib/api/types";
import { deploymentTriggerPermission } from "@/lib/environments";
import { DeploymentTable } from "./deployment-table";

export function ProjectDeployments({ project }: { project: Project }) {
  const { data, isPending, error } = useProjectDeployments(project.id);
  const { data: environments } = useEnvironments(project.id);
  const types: Record<string, EnvironmentType> = Object.fromEntries(
    (environments ?? []).map((environment) => [environment.id, environment.type]),
  );

  return (
    <QueryState isPending={isPending} error={error} errorTitle="Could not load deployments">
      {() =>
        data && (
          <DeploymentTable
            deployments={data.content}
            environmentTypes={types}
            canTrigger={(environmentId) =>
              !!types[environmentId] &&
              project.permissions.includes(deploymentTriggerPermission(types[environmentId]))
            }
          />
        )
      }
    </QueryState>
  );
}
