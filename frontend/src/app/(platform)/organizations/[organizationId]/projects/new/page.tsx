import type { Metadata } from "next";
import Link from "next/link";
import { PageHeader } from "@/components/page-header";
import { NewProjectForm } from "@/components/projects/new-project-form";

export const metadata: Metadata = { title: "New project" };

export default async function NewProjectPage({
  params,
}: PageProps<"/organizations/[organizationId]/projects/new">) {
  const { organizationId } = await params;
  return (
    <div className="space-y-6">
      <div className="text-sm text-muted-foreground">
        <Link href={`/organizations/${organizationId}`} className="hover:text-foreground">
          ← Back to organization
        </Link>
      </div>
      <PageHeader
        title="New project"
        description="Create a CloudFlow project from one of your GitHub repositories."
      />
      <NewProjectForm organizationId={organizationId} />
    </div>
  );
}
