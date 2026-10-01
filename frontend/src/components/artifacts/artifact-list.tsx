"use client";

import { DownloadIcon } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { QueryState } from "@/components/query-state";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { useArtifacts } from "@/hooks/use-artifacts";
import { artifactsApi } from "@/lib/api/artifacts";
import { errorMessage } from "@/lib/api/errors";
import type { Artifact, ArtifactKind } from "@/lib/api/types";
import { formatRelativeTime } from "@/lib/format";
import { formatBytes } from "@/lib/units";

export const ARTIFACT_KIND_LABELS: Record<ArtifactKind, string> = {
  BUILD_ARTIFACT: "Build artifact",
  GENERATED_FILE: "Generated file",
  ARCHIVED_LOG: "Archived log",
};

interface ArtifactListProps {
  projectId: string;
  /** Only this deployment's artifacts. */
  deploymentId?: string;
  live?: boolean;
}

export function ArtifactList({ projectId, deploymentId, live = false }: ArtifactListProps) {
  const { data, isPending, error } = useArtifacts(projectId, deploymentId, live);

  return (
    <QueryState isPending={isPending} error={error} errorTitle="Could not load artifacts">
      {() =>
        data &&
        (data.content.length === 0 ? (
          <p className="text-sm text-muted-foreground">
            {deploymentId
              ? "No artifacts for this deployment yet. The Dockerfile, vulnerability report, and full log are stored in S3 when artifact storage is configured."
              : "No artifacts yet. Build outputs, committed workflow and AI files, and archived deployment logs appear here when artifact storage is configured."}
          </p>
        ) : (
          <ul className="divide-y rounded-md border">
            {data.content.map((artifact) => (
              <ArtifactRow key={artifact.id} artifact={artifact} />
            ))}
          </ul>
        ))
      }
    </QueryState>
  );
}

function ArtifactRow({ artifact }: { artifact: Artifact }) {
  const [downloading, setDownloading] = useState(false);

  async function download() {
    setDownloading(true);
    try {
      const blob = await artifactsApi.download(artifact.id);
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = artifact.name.split("/").pop() || "artifact";
      link.click();
      // Revoking right away can cancel the download in some browsers.
      setTimeout(() => URL.revokeObjectURL(url), 10_000);
    } catch (error) {
      toast.error(errorMessage(error));
    } finally {
      setDownloading(false);
    }
  }

  return (
    <li className="flex flex-wrap items-center gap-3 px-3 py-2 text-sm">
      <div className="min-w-0 flex-1">
        <p className="truncate font-mono text-xs" title={artifact.name}>
          {artifact.name}
        </p>
        <p className="text-xs text-muted-foreground">
          {formatBytes(artifact.sizeBytes)} · {formatRelativeTime(artifact.createdAt)} · sha256{" "}
          <span title={artifact.sha256}>{artifact.sha256.slice(0, 12)}</span>
        </p>
      </div>
      <Badge variant="secondary">{ARTIFACT_KIND_LABELS[artifact.kind]}</Badge>
      <Button
        size="sm"
        variant="outline"
        onClick={download}
        disabled={downloading}
        aria-label={`Download ${artifact.name}`}
      >
        <DownloadIcon aria-hidden />
        {downloading ? "Downloading…" : "Download"}
      </Button>
    </li>
  );
}
