"use client";

import { KeyRoundIcon } from "lucide-react";
import { useState, type FormEvent } from "react";
import { toast } from "sonner";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { CopyButton } from "@/components/copy-button";
import { QueryState } from "@/components/query-state";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { useCreateDeployToken, useDeployTokens, useRevokeDeployToken } from "@/hooks/use-pipelines";
import { errorMessage } from "@/lib/api/errors";
import type { CreatedDeployToken, Environment } from "@/lib/api/types";
import { formatRelativeTime } from "@/lib/format";

interface DeployTokensPanelProps {
  environment: Environment;
  canManage: boolean;
}

/** Deploy tokens let the environment's GitHub Actions pipeline trigger deployments. */
export function DeployTokensPanel({ environment, canManage }: DeployTokensPanelProps) {
  const tokens = useDeployTokens(environment.id);
  const create = useCreateDeployToken(environment.id);
  const revoke = useRevokeDeployToken(environment.id);
  const [name, setName] = useState("github-actions");
  const [created, setCreated] = useState<CreatedDeployToken | null>(null);

  function handleCreate(event: FormEvent) {
    event.preventDefault();
    create.mutate(name.trim(), {
      onSuccess: (result) => setCreated(result),
      onError: (e) => toast.error(errorMessage(e)),
    });
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <KeyRoundIcon className="size-4" />
          Deploy tokens
        </CardTitle>
        <CardDescription>
          Used by the CI/CD pipeline to deploy this environment. A token acts with its
          creator&apos;s permissions.
        </CardDescription>
      </CardHeader>
      <CardContent className="grid gap-4">
        {created && (
          <Alert>
            <AlertTitle>Copy the token now: it will not be shown again</AlertTitle>
            <AlertDescription className="grid gap-2">
              <span>
                Save it as the GitHub repository secret <code>{created.secretName}</code>.
              </span>
              <div className="flex items-center gap-2">
                <code className="rounded bg-muted px-2 py-1 text-xs break-all">
                  {created.token}
                </code>
                <CopyButton value={created.token} />
              </div>
            </AlertDescription>
          </Alert>
        )}
        {canManage && (
          <form onSubmit={handleCreate} className="flex gap-2">
            <Input
              aria-label="Token name"
              value={name}
              onChange={(event) => setName(event.target.value)}
              maxLength={100}
              required
            />
            <Button type="submit" disabled={create.isPending || !name.trim()}>
              Create token
            </Button>
          </form>
        )}
        <QueryState isPending={tokens.isPending} error={tokens.error} skeletonClassName="h-16">
          {() => (
            <ul className="divide-y rounded-lg border text-sm">
              {tokens.data?.map((token) => (
                <li key={token.id} className="flex items-center justify-between gap-2 px-3 py-2">
                  <div>
                    <div className="font-medium">
                      {token.name}{" "}
                      <code className="text-xs text-muted-foreground">{token.tokenPrefix}…</code>
                    </div>
                    <div className="text-xs text-muted-foreground">
                      created {formatRelativeTime(token.createdAt)} · last used{" "}
                      {token.lastUsedAt ? formatRelativeTime(token.lastUsedAt) : "never"}
                      {token.revokedAt && " · revoked"}
                    </div>
                  </div>
                  {canManage && !token.revokedAt && (
                    <ConfirmDialog
                      trigger={
                        <Button variant="ghost" size="sm">
                          Revoke
                        </Button>
                      }
                      title={`Revoke ${token.name}?`}
                      description="Pipelines using this token can no longer deploy."
                      confirmLabel="Revoke"
                      onConfirm={() =>
                        revoke.mutate(token.id, {
                          onSuccess: () => toast.success("Token revoked"),
                          onError: (e) => toast.error(errorMessage(e)),
                        })
                      }
                    />
                  )}
                </li>
              ))}
              {tokens.data?.length === 0 && (
                <li className="px-3 py-4 text-muted-foreground">No deploy tokens.</li>
              )}
            </ul>
          )}
        </QueryState>
      </CardContent>
    </Card>
  );
}
