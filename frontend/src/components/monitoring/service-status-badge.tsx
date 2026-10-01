import { CircleCheckIcon, CircleDashedIcon, CircleXIcon, TriangleAlertIcon } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import type { ServiceStatus } from "@/lib/api/types";

/** Service state with icon + label (status is never conveyed by color alone). */
export function ServiceStatusBadge({ status }: { status: ServiceStatus }) {
  switch (status) {
    case "UP":
      return (
        <Badge className="bg-emerald-600 text-white">
          <CircleCheckIcon />
          Healthy
        </Badge>
      );
    case "DEGRADED":
      return (
        <Badge className="bg-amber-500 text-black">
          <TriangleAlertIcon />
          Degraded
        </Badge>
      );
    case "DOWN":
      return (
        <Badge variant="destructive">
          <CircleXIcon />
          Down
        </Badge>
      );
    case "NOT_DEPLOYED":
      return (
        <Badge variant="outline">
          <CircleDashedIcon />
          Not deployed
        </Badge>
      );
    default:
      return (
        <Badge variant="outline">
          <CircleDashedIcon />
          Waiting for data
        </Badge>
      );
  }
}
