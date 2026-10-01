"use client";

import { GitBranchIcon, LockIcon, PlusIcon } from "lucide-react";
import Link from "next/link";
import { AppTypeBadge } from "@/components/app-type-badge";
import { QueryState } from "@/components/query-state";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { useProjects } from "@/hooks/use-projects";
import type { Organization } from "@/lib/api/types";
import { formatRelativeTime } from "@/lib/format";

export function ProjectList({ organization }: { organization: Organization }) {
  const { data: projects, isPending, error } = useProjects(organization.id);
  const canCreate = organization.permissions.includes("PROJECT_WRITE");

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-4">
        <p className="text-sm text-muted-foreground">
          Projects are created from GitHub repositories.
        </p>
        {canCreate && (
          <Link
            href={`/organizations/${organization.id}/projects/new`}
            className={buttonVariants()}
          >
            <PlusIcon />
            New project
          </Link>
        )}
      </div>
      <QueryState isPending={isPending} error={error} errorTitle="Could not load projects">
        {() =>
          projects && projects.length > 0 ? (
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
              {projects.map((project) => (
                <Link key={project.id} href={`/projects/${project.id}`} className="group">
                  <Card className="h-full transition-colors group-hover:border-foreground/20">
                    <CardHeader>
                      <div className="flex items-start justify-between gap-2">
                        <CardTitle>{project.name}</CardTitle>
                        <AppTypeBadge appType={project.appType} />
                      </div>
                      <CardDescription className="flex items-center gap-1.5">
                        {project.repository.isPrivate && <LockIcon className="size-3" />}
                        {project.repository.fullName}
                      </CardDescription>
                    </CardHeader>
                    <CardContent className="flex items-center gap-4 text-xs text-muted-foreground">
                      <span className="flex items-center gap-1">
                        <GitBranchIcon className="size-3" />
                        {project.repository.defaultBranch}
                      </span>
                      <span>Updated {formatRelativeTime(project.updatedAt)}</span>
                    </CardContent>
                  </Card>
                </Link>
              ))}
            </div>
          ) : (
            <div className="rounded-xl border border-dashed p-10 text-center text-sm text-muted-foreground">
              No projects yet.
              {canCreate && " Create one from a GitHub repository to get started."}
            </div>
          )
        }
      </QueryState>
    </div>
  );
}
