import type { Metadata } from "next";
import { EnvironmentView } from "@/components/environments/environment-view";

export const metadata: Metadata = { title: "Environment" };

export default async function EnvironmentPage({
  params,
}: PageProps<"/environments/[environmentId]">) {
  const { environmentId } = await params;
  return <EnvironmentView environmentId={environmentId} />;
}
