import type { Metadata } from "next";
import { PageHeader } from "@/components/page-header";
import { CreateOrganizationDialog } from "@/components/organizations/create-organization-dialog";
import { OrganizationList } from "@/components/organizations/organization-list";

export const metadata: Metadata = { title: "Organizations" };

export default function OrganizationsPage() {
  return (
    <div className="space-y-6">
      <PageHeader
        title="Organizations"
        description="Organizations group projects, environments, and the people who work on them."
        actions={<CreateOrganizationDialog />}
      />
      <OrganizationList />
    </div>
  );
}
