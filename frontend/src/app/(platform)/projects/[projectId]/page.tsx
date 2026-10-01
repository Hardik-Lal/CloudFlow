import type { Metadata } from "next";
import { ProjectDashboard } from "@/components/projects/project-dashboard";

export const metadata: Metadata = { title: "Project" };

export default async function ProjectPage({ params }: PageProps<"/projects/[projectId]">) {
  const { projectId } = await params;
  return <ProjectDashboard projectId={projectId} />;
}
