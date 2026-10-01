"use client";

import { useEffect } from "react";
import { PageHeader } from "@/components/page-header";
import { RoleBadge } from "@/components/role-badge";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useOrganization } from "@/hooks/use-organizations";
import { errorMessage } from "@/lib/api/errors";
import { useOrganizationStore } from "@/stores/organization-store";
import { ProjectList } from "@/components/projects/project-list";
import { AuditLogPanel } from "./audit-log-panel";
import { MembersPanel } from "./members-panel";
import { OrganizationSettings } from "./organization-settings";

export function OrganizationDetail({ organizationId }: { organizationId: string }) {
  const { data: organization, isPending, error } = useOrganization(organizationId);
  const selectOrganization = useOrganizationStore((state) => state.selectOrganization);

  useEffect(() => {
    if (organization) {
      selectOrganization(organization.id);
    }
  }, [organization, selectOrganization]);

  if (isPending) {
    return <Skeleton className="h-48" />;
  }
  if (error) {
    return (
      <Alert variant="destructive">
        <AlertTitle>Organization unavailable</AlertTitle>
        <AlertDescription>{errorMessage(error)}</AlertDescription>
      </Alert>
    );
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title={
          <span className="flex items-center gap-3">
            {organization.name}
            <RoleBadge role={organization.role} />
          </span>
        }
        description={organization.slug}
      />
      <Tabs defaultValue="projects">
        <TabsList>
          <TabsTrigger value="projects">Projects</TabsTrigger>
          <TabsTrigger value="members">Members</TabsTrigger>
          {organization.permissions.includes("AUDIT_READ") && (
            <TabsTrigger value="audit">Audit log</TabsTrigger>
          )}
          <TabsTrigger value="settings">Settings</TabsTrigger>
        </TabsList>
        <TabsContent value="projects" className="pt-4">
          <ProjectList organization={organization} />
        </TabsContent>
        <TabsContent value="members" className="pt-4">
          <MembersPanel organization={organization} />
        </TabsContent>
        <TabsContent value="audit" className="pt-4">
          <AuditLogPanel organizationId={organization.id} />
        </TabsContent>
        <TabsContent value="settings" className="pt-4">
          <OrganizationSettings organization={organization} />
        </TabsContent>
      </Tabs>
    </div>
  );
}
