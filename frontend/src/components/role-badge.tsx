import { Badge } from "@/components/ui/badge";
import type { Role } from "@/lib/api/types";
import { ROLE_LABELS } from "@/lib/roles";

const VARIANTS: Record<Role, "default" | "secondary" | "outline"> = {
  OWNER: "default",
  ADMIN: "secondary",
  DEVELOPER: "outline",
  VIEWER: "outline",
};

export function RoleBadge({ role }: { role: Role }) {
  return <Badge variant={VARIANTS[role]}>{ROLE_LABELS[role]}</Badge>;
}
