"use client";

import { useState } from "react";
import { QueryState } from "@/components/query-state";
import { UserAvatar } from "@/components/user-avatar";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useBranches, useCommits } from "@/hooks/use-projects";
import type { Project } from "@/lib/api/types";
import { formatRelativeTime } from "@/lib/format";

export function CommitList({ project }: { project: Project }) {
  const [branch, setBranch] = useState(project.repository.defaultBranch);
  const { data: branches } = useBranches(project.id);
  const { data: commits, isPending, error } = useCommits(project.id, branch);
  const branchNames = branches?.map((b) => b.name) ?? [project.repository.defaultBranch];

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between gap-2">
        <CardTitle>Recent commits</CardTitle>
        <Select
          items={Object.fromEntries(branchNames.map((name) => [name, name]))}
          value={branch}
          onValueChange={(value) => value && setBranch(value)}
        >
          <SelectTrigger size="sm" className="max-w-48" aria-label="Branch">
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
      </CardHeader>
      <CardContent>
        <QueryState isPending={isPending} error={error} errorTitle="Could not load commits">
          {() => (
            <ul className="divide-y">
              {commits?.map((commit) => (
                <li key={commit.sha} className="flex items-center gap-3 py-2.5">
                  <UserAvatar
                    username={commit.authorLogin ?? commit.authorName ?? "?"}
                    avatarUrl={commit.authorAvatarUrl}
                    className="size-7"
                  />
                  <div className="min-w-0 flex-1">
                    <a
                      href={commit.htmlUrl}
                      target="_blank"
                      rel="noreferrer"
                      className="line-clamp-1 text-sm font-medium hover:underline"
                    >
                      {commit.message}
                    </a>
                    <div className="text-xs text-muted-foreground">
                      {commit.authorLogin ?? commit.authorName} ·{" "}
                      {formatRelativeTime(commit.committedAt)}
                    </div>
                  </div>
                  <code className="shrink-0 rounded bg-muted px-1.5 py-0.5 text-xs">
                    {commit.shortSha}
                  </code>
                </li>
              ))}
              {commits?.length === 0 && (
                <li className="py-4 text-sm text-muted-foreground">No commits on this branch.</li>
              )}
            </ul>
          )}
        </QueryState>
      </CardContent>
    </Card>
  );
}
