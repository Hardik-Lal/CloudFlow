import {
  BanIcon,
  CircleCheckIcon,
  CircleDashedIcon,
  CircleXIcon,
  LoaderCircleIcon,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { runOutcome } from "@/lib/pipelines";

export function RunOutcomeBadge({
  status,
  conclusion,
}: {
  status: string;
  conclusion: string | null;
}) {
  switch (runOutcome({ status, conclusion })) {
    case "running":
      return (
        <Badge variant="secondary">
          <LoaderCircleIcon className="animate-spin" />
          {status.replace("_", " ")}
        </Badge>
      );
    case "success":
      return (
        <Badge className="bg-emerald-600 text-white">
          <CircleCheckIcon />
          success
        </Badge>
      );
    case "failure":
      return (
        <Badge variant="destructive">
          <CircleXIcon />
          {conclusion}
        </Badge>
      );
    case "cancelled":
      return (
        <Badge variant="outline">
          <BanIcon />
          cancelled
        </Badge>
      );
    default:
      return (
        <Badge variant="outline">
          <CircleDashedIcon />
          {conclusion ?? status}
        </Badge>
      );
  }
}
