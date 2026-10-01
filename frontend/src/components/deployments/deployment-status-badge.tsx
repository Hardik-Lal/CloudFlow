import { CircleCheckIcon, CircleXIcon, LoaderCircleIcon, UndoIcon, BanIcon } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import type { DeploymentStatus } from "@/lib/api/types";
import { isInProgress, STATUS_LABELS } from "@/lib/deployments";

export function DeploymentStatusBadge({ status }: { status: DeploymentStatus }) {
  if (isInProgress(status)) {
    return (
      <Badge variant="secondary">
        <LoaderCircleIcon className="animate-spin" />
        {STATUS_LABELS[status]}
      </Badge>
    );
  }
  switch (status) {
    case "SUCCEEDED":
      return (
        <Badge className="bg-emerald-600 text-white">
          <CircleCheckIcon />
          {STATUS_LABELS[status]}
        </Badge>
      );
    case "FAILED":
      return (
        <Badge variant="destructive">
          <CircleXIcon />
          {STATUS_LABELS[status]}
        </Badge>
      );
    case "ROLLED_BACK":
      return (
        <Badge variant="outline">
          <UndoIcon />
          {STATUS_LABELS[status]}
        </Badge>
      );
    default:
      return (
        <Badge variant="outline">
          <BanIcon />
          {STATUS_LABELS[status]}
        </Badge>
      );
  }
}
