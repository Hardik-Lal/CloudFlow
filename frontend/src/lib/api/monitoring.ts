import { apiFetch } from "./client";
import type { EnvironmentEvent, EnvironmentMetrics, RuntimeLogLine } from "./types";

export const monitoringApi = {
  metrics: (environmentId: string) =>
    apiFetch<EnvironmentMetrics>(`/api/v1/environments/${environmentId}/metrics`),
  events: (environmentId: string, limit = 50) =>
    apiFetch<EnvironmentEvent[]>(`/api/v1/environments/${environmentId}/events?limit=${limit}`),
  runtimeLogs: (environmentId: string, tail = 200) =>
    apiFetch<RuntimeLogLine[]>(`/api/v1/environments/${environmentId}/runtime-logs?tail=${tail}`),
};
