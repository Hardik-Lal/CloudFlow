import type { Role } from "@/lib/api/types";

export const ROLE_LABELS: Record<Role, string> = {
  OWNER: "Owner",
  ADMIN: "Admin",
  DEVELOPER: "Developer",
  VIEWER: "Viewer",
};

export const ROLE_DESCRIPTIONS: Record<Role, string> = {
  OWNER: "Full control, including deleting the organization and managing owners",
  ADMIN: "Manages members, settings, and production",
  DEVELOPER: "Builds and deploys to development and staging",
  VIEWER: "Read-only access",
};

/**
 * Mirrors the backend rule (Role.canManage) to decide which controls to show. The API enforces the
 * rule regardless: Owners manage everyone; Admins manage only Developers and Viewers.
 */
export function canManageMember(actorRole: Role, targetRole: Role): boolean {
  if (actorRole === "OWNER") {
    return true;
  }
  return actorRole === "ADMIN" && (targetRole === "DEVELOPER" || targetRole === "VIEWER");
}

/** Roles the actor may assign; only Owners can grant the Owner role. */
export function assignableRoles(actorRole: Role): Role[] {
  if (actorRole === "OWNER") {
    return ["OWNER", "ADMIN", "DEVELOPER", "VIEWER"];
  }
  return actorRole === "ADMIN" ? ["ADMIN", "DEVELOPER", "VIEWER"] : [];
}
