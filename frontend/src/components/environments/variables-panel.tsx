"use client";

import { LockIcon, PencilIcon, PlusIcon, Trash2Icon } from "lucide-react";
import { toast } from "sonner";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { QueryState } from "@/components/query-state";
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { useDeleteVariable, useVariables } from "@/hooks/use-environments";
import { errorMessage } from "@/lib/api/errors";
import type { Environment } from "@/lib/api/types";
import { formatRelativeTime } from "@/lib/format";
import { VariableDialog } from "./variable-dialog";

interface VariablesPanelProps {
  environment: Environment;
  canWrite: boolean;
}

export function VariablesPanel({ environment, canWrite }: VariablesPanelProps) {
  const { data: variables, isPending, error } = useVariables(environment.id);
  const deleteVariable = useDeleteVariable(environment.id, environment.projectId);

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-4">
        <p className="text-sm text-muted-foreground">
          Secrets are encrypted at rest and never shown again after saving.
        </p>
        {canWrite && (
          <VariableDialog
            environmentId={environment.id}
            projectId={environment.projectId}
            trigger={
              <Button>
                <PlusIcon />
                Add variable
              </Button>
            }
          />
        )}
      </div>
      <QueryState isPending={isPending} error={error} errorTitle="Could not load variables">
        {() =>
          variables && variables.length > 0 ? (
            <div className="rounded-xl border">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Key</TableHead>
                    <TableHead>Value</TableHead>
                    <TableHead>Updated</TableHead>
                    <TableHead className="w-24" />
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {variables.map((variable) => (
                    <TableRow key={variable.key}>
                      <TableCell className="font-mono text-xs font-medium">
                        {variable.key}
                      </TableCell>
                      <TableCell className="max-w-md font-mono text-xs">
                        {variable.secret ? (
                          <span className="flex items-center gap-1.5 text-muted-foreground">
                            <LockIcon className="size-3" />
                            ••••••••
                          </span>
                        ) : (
                          <span className="line-clamp-1 break-all">{variable.value}</span>
                        )}
                      </TableCell>
                      <TableCell className="text-muted-foreground">
                        {formatRelativeTime(variable.updatedAt)}
                      </TableCell>
                      <TableCell className="text-right">
                        {canWrite && (
                          <div className="flex justify-end gap-1">
                            <VariableDialog
                              environmentId={environment.id}
                              projectId={environment.projectId}
                              variable={variable}
                              trigger={
                                <Button
                                  variant="ghost"
                                  size="icon-sm"
                                  aria-label={`Edit ${variable.key}`}
                                >
                                  <PencilIcon />
                                </Button>
                              }
                            />
                            <ConfirmDialog
                              trigger={
                                <Button
                                  variant="ghost"
                                  size="icon-sm"
                                  aria-label={`Delete ${variable.key}`}
                                >
                                  <Trash2Icon />
                                </Button>
                              }
                              title={`Delete ${variable.key}?`}
                              description="The variable is removed from future deployments."
                              confirmLabel="Delete"
                              onConfirm={() =>
                                deleteVariable.mutate(variable.key, {
                                  onSuccess: () => toast.success(`${variable.key} deleted`),
                                  onError: (e) => toast.error(errorMessage(e)),
                                })
                              }
                            />
                          </div>
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          ) : (
            <div className="rounded-xl border border-dashed p-10 text-center text-sm text-muted-foreground">
              No variables yet.
            </div>
          )
        }
      </QueryState>
    </div>
  );
}
