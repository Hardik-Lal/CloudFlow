import type { AppType } from "@/lib/api/types";

export const APP_TYPE_LABELS: Record<AppType, string> = {
  DOCKERFILE: "Dockerfile",
  JAVA_MAVEN: "Java · Maven",
  JAVA_GRADLE: "Java · Gradle",
  NODE: "Node.js",
  PYTHON: "Python",
  UNKNOWN: "Unknown",
};
