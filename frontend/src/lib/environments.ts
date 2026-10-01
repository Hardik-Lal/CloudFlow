import type { EnvironmentType, Permission } from "@/lib/api/types";

export const ENVIRONMENT_LABELS: Record<EnvironmentType, string> = {
  DEVELOPMENT: "Development",
  STAGING: "Staging",
  PRODUCTION: "Production",
};

/** Permission needed to change an environment of the given type (see backend RBAC matrix). */
export function environmentWritePermission(type: EnvironmentType): Permission {
  return type === "PRODUCTION" ? "ENV_WRITE_PRODUCTION" : "ENV_WRITE";
}

/** Permission needed to deploy or roll back an environment of the given type. */
export function deploymentTriggerPermission(type: EnvironmentType): Permission {
  return type === "PRODUCTION" ? "DEPLOYMENT_TRIGGER_PRODUCTION" : "DEPLOYMENT_TRIGGER";
}
