import type { Metadata } from "next";
import { DeploymentDetail } from "@/components/deployments/deployment-detail";

export const metadata: Metadata = { title: "Deployment" };

export default async function DeploymentPage({ params }: PageProps<"/deployments/[deploymentId]">) {
  const { deploymentId } = await params;
  return <DeploymentDetail deploymentId={deploymentId} />;
}
