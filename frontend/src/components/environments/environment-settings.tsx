"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { toast } from "sonner";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { FormField } from "@/components/form-field";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useDeleteEnvironment, useUpdateEnvironmentBranch } from "@/hooks/use-environments";
import { useBranches } from "@/hooks/use-projects";
import { errorMessage } from "@/lib/api/errors";
import type { Environment } from "@/lib/api/types";
import { ENVIRONMENT_LABELS } from "@/lib/environments";

interface EnvironmentSettingsProps {
  environment: Environment;
  canWrite: boolean;
}

export function EnvironmentSettings({ environment, canWrite }: EnvironmentSettingsProps) {
  const [branch, setBranch] = useState(environment.branch);
  const { data: branches } = useBranches(environment.projectId);
  const updateBranch = useUpdateEnvironmentBranch(environment.id, environment.projectId);
  const deleteEnvironment = useDeleteEnvironment(environment.id, environment.projectId);
  const router = useRouter();
  const branchNames = branches?.map((b) => b.name) ?? [environment.branch];
  const label = ENVIRONMENT_LABELS[environment.type];

  return (
    <div className="grid gap-6">
      <Card>
        <CardHeader>
          <CardTitle>Deployed branch</CardTitle>
          <CardDescription>The Git branch CloudFlow builds for this environment.</CardDescription>
        </CardHeader>
        <CardContent className="py-4">
          <FormField id="environment-branch" label="Branch">
            <Select
              items={Object.fromEntries(branchNames.map((name) => [name, name]))}
              value={branch}
              onValueChange={(value) => value && setBranch(value)}
              disabled={!canWrite}
            >
              <SelectTrigger id="environment-branch" className="w-64">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {branchNames.map((name) => (
                  <SelectItem key={name} value={name}>
                    {name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </FormField>
        </CardContent>
        {canWrite && (
          <CardFooter>
            <Button
              disabled={updateBranch.isPending || branch === environment.branch}
              onClick={() =>
                updateBranch.mutate(branch, {
                  onSuccess: () => toast.success(`${label} now deploys ${branch}`),
                  onError: (e) => toast.error(errorMessage(e)),
                })
              }
            >
              Save
            </Button>
          </CardFooter>
        )}
      </Card>
      {canWrite && (
        <Card className="border-destructive/40">
          <CardHeader>
            <CardTitle>Delete environment</CardTitle>
            <CardDescription>
              Removes the environment with its variables and configuration.
            </CardDescription>
          </CardHeader>
          <CardFooter>
            <ConfirmDialog
              trigger={
                <Button variant="destructive" disabled={deleteEnvironment.isPending}>
                  Delete {label.toLowerCase()} environment
                </Button>
              }
              title={`Delete the ${label.toLowerCase()} environment?`}
              description="All of its variables, including secrets, are permanently deleted."
              confirmLabel="Delete"
              onConfirm={() =>
                deleteEnvironment.mutate(undefined, {
                  onSuccess: () => {
                    toast.success(`${label} environment deleted`);
                    router.replace(`/projects/${environment.projectId}`);
                  },
                  onError: (e) => toast.error(errorMessage(e)),
                })
              }
            />
          </CardFooter>
        </Card>
      )}
    </div>
  );
}
