import { ExternalLinkIcon } from "lucide-react";
import type { ReactNode } from "react";
import { AppTypeBadge } from "@/components/app-type-badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import type { Project } from "@/lib/api/types";
import { formatRelativeTime } from "@/lib/format";

export function RepositoryCard({ project }: { project: Project }) {
  const { repository } = project;
  const rows: [string, ReactNode][] = [
    [
      "Repository",
      <a
        key="repo"
        href={repository.htmlUrl}
        target="_blank"
        rel="noreferrer"
        className="inline-flex items-center gap-1 hover:underline"
      >
        {repository.fullName}
        <ExternalLinkIcon className="size-3" />
      </a>,
    ],
    ["Visibility", repository.isPrivate ? "Private" : "Public"],
    ["Default branch", repository.defaultBranch],
    ["Language", repository.language ?? "—"],
    ["Detected type", <AppTypeBadge key="type" appType={project.appType} />],
    ["Last synced", formatRelativeTime(repository.lastSyncedAt)],
  ];

  return (
    <Card>
      <CardHeader>
        <CardTitle>Repository</CardTitle>
      </CardHeader>
      <CardContent>
        <dl className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-2 text-sm">
          {rows.map(([label, value]) => (
            <div key={label} className="contents">
              <dt className="text-muted-foreground">{label}</dt>
              <dd>{value}</dd>
            </div>
          ))}
        </dl>
      </CardContent>
    </Card>
  );
}
