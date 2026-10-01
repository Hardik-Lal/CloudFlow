"use client";

import { GitBranchIcon, KeyRoundIcon, PlusIcon } from "lucide-react";
import Link from "next/link";
import { toast } from "sonner";
import { QueryState } from "@/components/query-state";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { useCreateEnvironment, useEnvironments } from "@/hooks/use-environments";
import { errorMessage } from "@/lib/api/errors";
import {
  ENVIRONMENT_TYPES,
  type Environment,
  type EnvironmentType,
  type Project,
} from "@/lib/api/types";
import { ENVIRONMENT_LABELS, environmentWritePermission } from "@/lib/environments";
import { EnvironmentTypeBadge } from "./environment-type-badge";

export function EnvironmentList({ project }: { project: Project }) {
  const { data: environments, isPending, error } = useEnvironments(project.id);

  return (
    <QueryState isPending={isPending} error={error} errorTitle="Could not load environments">
      {() => (
        <div className="grid gap-4 md:grid-cols-3">
          {ENVIRONMENT_TYPES.map((type) => {
            const environment = environments?.find((env) => env.type === type);
            return environment ? (
              <EnvironmentCard key={type} environment={environment} />
            ) : (
              <MissingEnvironmentCard key={type} project={project} type={type} />
            );
          })}
        </div>
      )}
    </QueryState>
  );
}

function EnvironmentCard({ environment }: { environment: Environment }) {
  return (
    <Link href={`/environments/${environment.id}`} className="group">
      <Card className="h-full transition-colors group-hover:border-foreground/20">
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle>{ENVIRONMENT_LABELS[environment.type]}</CardTitle>
            <EnvironmentTypeBadge type={environment.type} />
          </div>
          <CardDescription className="flex items-center gap-1.5">
            <GitBranchIcon className="size-3" />
            {environment.branch}
          </CardDescription>
        </CardHeader>
        <CardContent className="grid gap-1 text-sm text-muted-foreground">
          {environment.config && (
            <span>
              {environment.config.template} template · port {environment.config.containerPort}
            </span>
          )}
          <span className="flex items-center gap-1.5">
            <KeyRoundIcon className="size-3" />
            {environment.variableCount} variables ({environment.secretCount} secret)
          </span>
        </CardContent>
      </Card>
    </Link>
  );
}

function MissingEnvironmentCard({ project, type }: { project: Project; type: EnvironmentType }) {
  const createEnvironment = useCreateEnvironment(project.id);
  const canCreate = project.permissions.includes(environmentWritePermission(type));

  return (
    <Card className="h-full border-dashed">
      <CardHeader>
        <CardTitle className="text-muted-foreground">{ENVIRONMENT_LABELS[type]}</CardTitle>
        <CardDescription>Not set up yet.</CardDescription>
      </CardHeader>
      <CardContent>
        {canCreate ? (
          <Button
            variant="outline"
            disabled={createEnvironment.isPending}
            onClick={() =>
              createEnvironment.mutate(
                { type },
                {
                  onSuccess: () => toast.success(`${ENVIRONMENT_LABELS[type]} environment created`),
                  onError: (error) => toast.error(errorMessage(error)),
                },
              )
            }
          >
            <PlusIcon />
            Create
          </Button>
        ) : (
          <p className="text-xs text-muted-foreground">
            {type === "PRODUCTION"
              ? "Only owners and admins can create the production environment."
              : "You do not have permission to create environments."}
          </p>
        )}
      </CardContent>
    </Card>
  );
}
