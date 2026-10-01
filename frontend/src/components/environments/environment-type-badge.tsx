import { Badge } from "@/components/ui/badge";
import type { EnvironmentType } from "@/lib/api/types";
import { ENVIRONMENT_LABELS } from "@/lib/environments";

export function EnvironmentTypeBadge({ type }: { type: EnvironmentType }) {
  return (
    <Badge variant={type === "PRODUCTION" ? "destructive" : "secondary"}>
      {ENVIRONMENT_LABELS[type]}
    </Badge>
  );
}
