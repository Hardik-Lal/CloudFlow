"use client";

import { GitBranchIcon } from "lucide-react";
import Link from "next/link";
import { PageHeader } from "@/components/page-header";
import { QueryState } from "@/components/query-state";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useEnvironment } from "@/hooks/use-environments";
import { useProject } from "@/hooks/use-projects";
import type { Environment, Project } from "@/lib/api/types";
import { DeployButton } from "@/components/deployments/deploy-button";
import { EnvironmentDeployments } from "@/components/deployments/environment-deployments";
import { LiveLogsPanel } from "@/components/monitoring/live-logs-panel";
import { MonitoringPanel } from "@/components/monitoring/monitoring-panel";
import {
  deploymentTriggerPermission,
  ENVIRONMENT_LABELS,
  environmentWritePermission,
} from "@/lib/environments";
import { ConfigForm } from "./config-form";
import { DeployTokensPanel } from "./deploy-tokens-panel";
import { EnvironmentSettings } from "./environment-settings";
import { EnvironmentTypeBadge } from "./environment-type-badge";
import { VariablesPanel } from "./variables-panel";

export function EnvironmentView({ environmentId }: { environmentId: string }) {
  const environment = useEnvironment(environmentId);
  const projectId = environment.data?.projectId;

  return (
    <QueryState
      isPending={environment.isPending}
      error={environment.error}
      errorTitle="Environment unavailable"
      skeletonClassName="h-48"
    >
      {() =>
        environment.data &&
        projectId && <EnvironmentWithProject environment={environment.data} projectId={projectId} />
      }
    </QueryState>
  );
}

function EnvironmentWithProject({
  environment,
  projectId,
}: {
  environment: Environment;
  projectId: string;
}) {
  const project = useProject(projectId);
  return (
    <QueryState isPending={project.isPending} error={project.error} skeletonClassName="h-48">
      {() => project.data && <EnvironmentDetail environment={environment} project={project.data} />}
    </QueryState>
  );
}

function EnvironmentDetail({
  environment,
  project,
}: {
  environment: Environment;
  project: Project;
}) {
  const canWrite = project.permissions.includes(environmentWritePermission(environment.type));
  const canDeploy = project.permissions.includes(deploymentTriggerPermission(environment.type));

  return (
    <div className="space-y-6">
      <div className="text-sm text-muted-foreground">
        <Link href={`/projects/${project.id}`} className="hover:text-foreground">
          ← {project.name}
        </Link>
      </div>
      <PageHeader
        title={
          <span className="flex items-center gap-3">
            {ENVIRONMENT_LABELS[environment.type]}
            <EnvironmentTypeBadge type={environment.type} />
          </span>
        }
        description={
          <span className="flex items-center gap-1.5">
            <GitBranchIcon className="size-3" />
            {environment.branch} · {project.repository.fullName}
            {!canWrite && " · read-only for your role"}
          </span>
        }
        actions={canDeploy && <DeployButton environment={environment} />}
      />
      <Tabs defaultValue="deployments">
        <TabsList>
          <TabsTrigger value="deployments">Deployments</TabsTrigger>
          <TabsTrigger value="monitoring">Monitoring</TabsTrigger>
          <TabsTrigger value="logs">Live logs</TabsTrigger>
          <TabsTrigger value="variables">Variables</TabsTrigger>
          <TabsTrigger value="configuration">Configuration</TabsTrigger>
          <TabsTrigger value="settings">Settings</TabsTrigger>
        </TabsList>
        <TabsContent value="deployments" className="pt-4">
          <EnvironmentDeployments environmentId={environment.id} canTrigger={canDeploy} />
        </TabsContent>
        <TabsContent value="monitoring" className="pt-4">
          <MonitoringPanel environmentId={environment.id} />
        </TabsContent>
        <TabsContent value="logs" className="pt-4">
          <LiveLogsPanel environmentId={environment.id} />
        </TabsContent>
        <TabsContent value="variables" className="pt-4">
          <VariablesPanel environment={environment} canWrite={canWrite} />
        </TabsContent>
        <TabsContent value="configuration" className="pt-4">
          <ConfigForm environment={environment} appType={project.appType} canWrite={canWrite} />
        </TabsContent>
        <TabsContent value="settings" className="pt-4">
          <div className="grid max-w-2xl gap-6">
            <EnvironmentSettings environment={environment} canWrite={canWrite} />
            <DeployTokensPanel environment={environment} canManage={canDeploy} />
          </div>
        </TabsContent>
      </Tabs>
    </div>
  );
}
