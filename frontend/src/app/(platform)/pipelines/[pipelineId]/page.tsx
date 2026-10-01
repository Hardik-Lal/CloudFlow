import type { Metadata } from "next";
import { PipelineDetail } from "@/components/pipelines/pipeline-detail";

export const metadata: Metadata = { title: "Pipeline" };

export default async function PipelinePage({ params }: PageProps<"/pipelines/[pipelineId]">) {
  const { pipelineId } = await params;
  return <PipelineDetail pipelineId={pipelineId} />;
}
