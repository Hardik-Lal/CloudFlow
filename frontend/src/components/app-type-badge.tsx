import { Badge } from "@/components/ui/badge";
import type { AppType } from "@/lib/api/types";
import { APP_TYPE_LABELS } from "@/lib/app-types";

export function AppTypeBadge({ appType }: { appType: AppType }) {
  return <Badge variant="outline">{APP_TYPE_LABELS[appType]}</Badge>;
}
