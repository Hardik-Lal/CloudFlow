"use client";

import { LockIcon, RefreshCwIcon } from "lucide-react";
import Link from "next/link";
import { toast } from "sonner";
import { AppTypeBadge } from "@/components/app-type-badge";
import { PageHeader } from "@/components/page-header";
import { QueryState } from "@/components/query-state";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useProject, useSyncProject } from "@/hooks/use-projects";
import { errorMessage } from "@/lib/api/errors";
import type { Project } from "@/lib/api/types";
import { AssistantPanel } from "@/components/assistant/assistant-panel";
import { ArtifactList } from "@/components/artifacts/artifact-list";
import { ProjectDeployments } from "@/components/deployments/project-deployments";
import { EnvironmentList } from "@/components/environments/environment-list";
import { ProjectPipelines } from "@/components/pipelines/project-pipelines";
import { BranchTable } from "./branch-table";
import { CommitList } from "./commit-list";
import { ProjectSettings } from "./project-settings";
import { PullRequestList } from "./pull-request-list";
import { RepositoryCard } from "./repository-card";

export function ProjectDashboard({ projectId }: { projectId: string }) {
  const { data: project, isPending, error } = useProject(projectId);

  return (
    <QueryState
      isPending={isPending}
      error={error}
      errorTitle="Project unavailable"
      skeletonClassName="h-48"
    >
      {() => project && <ProjectView project={project} />}
    </QueryState>
  );
}

function ProjectView({ project }: { project: Project }) {
  const sync = useSyncProject(project.id);
  const canSync = project.permissions.includes("PROJECT_WRITE");

  return (
    <div className="space-y-6">
      <div className="text-sm text-muted-foreground">
        <Link href={`/organizations/${project.organizationId}`} className="hover:text-foreground">
          ← Back to organization
        </Link>
      </div>
      <PageHeader
        title={
          <span className="flex items-center gap-3">
            {project.name}
            <AppTypeBadge appType={project.appType} />
          </span>
        }
        description={
          <span className="flex items-center gap-1.5">
            {project.repository.isPrivate && <LockIcon className="size-3" />}
            {project.repository.fullName}
            {project.description && <> · {project.description}</>}
          </span>
        }
        actions={
          canSync && (
            <Button
              variant="outline"
              disabled={sync.isPending}
              onClick={() =>
                sync.mutate(undefined, {
                  onSuccess: () => toast.success("Synced with GitHub"),
                  onError: (e) => toast.error(errorMessage(e)),
                })
              }
            >
              <RefreshCwIcon className={sync.isPending ? "animate-spin" : undefined} />
              Sync with GitHub
            </Button>
          )
        }
      />
      <Tabs defaultValue="overview">
        <TabsList>
          <TabsTrigger value="overview">Overview</TabsTrigger>
          <TabsTrigger value="environments">Environments</TabsTrigger>
          <TabsTrigger value="deployments">Deployments</TabsTrigger>
          <TabsTrigger value="pipelines">CI/CD</TabsTrigger>
          <TabsTrigger value="artifacts">Artifacts</TabsTrigger>
          <TabsTrigger value="assistant">AI Assistant</TabsTrigger>
          <TabsTrigger value="branches">Branches</TabsTrigger>
          <TabsTrigger value="settings">Settings</TabsTrigger>
        </TabsList>
        <TabsContent value="overview" className="pt-4">
          <div className="grid gap-6 lg:grid-cols-3">
            <div className="space-y-6 lg:col-span-2">
              <CommitList project={project} />
              <PullRequestList projectId={project.id} />
            </div>
            <RepositoryCard project={project} />
          </div>
        </TabsContent>
        <TabsContent value="environments" className="pt-4">
          <EnvironmentList project={project} />
        </TabsContent>
        <TabsContent value="deployments" className="pt-4">
          <ProjectDeployments project={project} />
        </TabsContent>
        <TabsContent value="pipelines" className="pt-4">
          <ProjectPipelines project={project} />
        </TabsContent>
        <TabsContent value="artifacts" className="pt-4">
          <ArtifactList projectId={project.id} />
        </TabsContent>
        <TabsContent value="assistant" className="pt-4">
          <AssistantPanel project={project} />
        </TabsContent>
        <TabsContent value="branches" className="pt-4">
          <BranchTable project={project} />
        </TabsContent>
        <TabsContent value="settings" className="pt-4">
          <ProjectSettings project={project} />
        </TabsContent>
      </Tabs>
    </div>
  );
}
