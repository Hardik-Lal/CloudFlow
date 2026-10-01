"use client";

import { CircleCheckIcon, CircleXIcon, TriangleAlertIcon } from "lucide-react";
import { QueryState } from "@/components/query-state";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { useConfigValidation } from "@/hooks/use-environments";

export function ValidationPanel({ environmentId }: { environmentId: string }) {
  const { data: result, isPending, error } = useConfigValidation(environmentId);

  return (
    <Card>
      <CardHeader>
        <CardTitle>Pre-deployment validation</CardTitle>
      </CardHeader>
      <CardContent>
        <QueryState isPending={isPending} error={error} errorTitle="Validation failed to run">
          {() =>
            result && (
              <div className="grid gap-3 text-sm">
                <div className="flex items-center gap-2 font-medium">
                  {result.valid ? (
                    <>
                      <CircleCheckIcon className="size-4 text-emerald-600" />
                      Ready to deploy
                    </>
                  ) : (
                    <>
                      <CircleXIcon className="size-4 text-destructive" />
                      Deployment is blocked
                    </>
                  )}
                </div>
                <ul className="grid gap-2">
                  {result.errors.map((issue) => (
                    <li key={`e-${issue.field}-${issue.message}`} className="flex gap-2">
                      <CircleXIcon className="mt-0.5 size-3.5 shrink-0 text-destructive" />
                      <span>
                        <span className="font-mono text-xs">{issue.field}</span>: {issue.message}
                      </span>
                    </li>
                  ))}
                  {result.warnings.map((issue) => (
                    <li key={`w-${issue.field}-${issue.message}`} className="flex gap-2">
                      <TriangleAlertIcon className="mt-0.5 size-3.5 shrink-0 text-amber-500" />
                      <span>
                        <span className="font-mono text-xs">{issue.field}</span>: {issue.message}
                      </span>
                    </li>
                  ))}
                </ul>
              </div>
            )
          }
        </QueryState>
      </CardContent>
    </Card>
  );
}
