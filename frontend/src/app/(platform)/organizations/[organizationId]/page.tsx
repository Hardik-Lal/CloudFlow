import type { Metadata } from "next";
import { OrganizationDetail } from "@/components/organizations/organization-detail";

export const metadata: Metadata = { title: "Organization" };

export default async function OrganizationPage({
  params,
}: PageProps<"/organizations/[organizationId]">) {
  const { organizationId } = await params;
  return <OrganizationDetail organizationId={organizationId} />;
}
