"use client";

import { GitPullRequestIcon } from "lucide-react";
import { QueryState } from "@/components/query-state";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { usePullRequests } from "@/hooks/use-projects";
import { formatRelativeTime } from "@/lib/format";

export function PullRequestList({ projectId }: { projectId: string }) {
  const { data: pulls, isPending, error } = usePullRequests(projectId);

  return (
    <Card>
      <CardHeader>
        <CardTitle>Open pull requests</CardTitle>
      </CardHeader>
      <CardContent>
        <QueryState isPending={isPending} error={error} errorTitle="Could not load pull requests">
          {() => (
            <ul className="divide-y">
              {pulls?.map((pull) => (
                <li key={pull.number} className="flex items-start gap-3 py-2.5">
                  <GitPullRequestIcon className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
                  <div className="min-w-0 flex-1">
                    <a
                      href={pull.htmlUrl}
                      target="_blank"
                      rel="noreferrer"
                      className="line-clamp-1 text-sm font-medium hover:underline"
                    >
                      {pull.title}
                    </a>
                    <div className="text-xs text-muted-foreground">
                      #{pull.number} · {pull.headBranch} → {pull.baseBranch} · {pull.authorLogin} ·
                      updated {formatRelativeTime(pull.updatedAt)}
                    </div>
                  </div>
                  {pull.draft && <Badge variant="outline">Draft</Badge>}
                </li>
              ))}
              {pulls?.length === 0 && (
                <li className="py-4 text-sm text-muted-foreground">No open pull requests.</li>
              )}
            </ul>
          )}
        </QueryState>
      </CardContent>
    </Card>
  );
}
