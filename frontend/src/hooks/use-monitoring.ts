"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { monitoringApi } from "@/lib/api/monitoring";
import type { EnvironmentMetrics, MetricSample, RuntimeLogLine } from "@/lib/api/types";
import { queryKeys } from "@/lib/query-keys";
import { subscribe } from "@/lib/realtime";

const MAX_HISTORY = 240;
const MAX_LOG_LINES = 2000;

/** Metrics snapshot over REST, then live samples pushed over WebSocket. */
export function useEnvironmentMetrics(environmentId: string) {
  const queryClient = useQueryClient();
  const query = useQuery({
    queryKey: queryKeys.environments.metrics(environmentId),
    queryFn: () => monitoringApi.metrics(environmentId),
    // Summaries (uptime, error rate) come from the server; refresh them periodically.
    refetchInterval: 60_000,
  });

  useEffect(
    () =>
      subscribe<MetricSample>(`/topic/environments/${environmentId}/metrics`, (sample) =>
        queryClient.setQueryData<EnvironmentMetrics>(
          queryKeys.environments.metrics(environmentId),
          (current) =>
            current && current.deploymentId === sample.deploymentId
              ? {
                  ...current,
                  current: sample,
                  status: !sample.running ? "DOWN" : sample.healthy === false ? "DEGRADED" : "UP",
                  history: [...current.history, sample].slice(-MAX_HISTORY),
                }
              : current,
        ),
      ),
    [environmentId, queryClient],
  );
  return query;
}

export function useEnvironmentEvents(environmentId: string) {
  return useQuery({
    queryKey: queryKeys.environments.events(environmentId),
    queryFn: () => monitoringApi.events(environmentId),
    refetchInterval: 15_000,
  });
}

/** Recent container output over REST, then live lines over WebSocket. */
export function useRuntimeLogs(environmentId: string) {
  const [lines, setLines] = useState<RuntimeLogLine[]>([]);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    let cancelled = false;
    const buffered: RuntimeLogLine[] = [];
    let loaded = false;
    const append = (next: RuntimeLogLine[]) =>
      setLines((current) => [...current, ...next].slice(-MAX_LOG_LINES));

    // Subscribe first so nothing is missed while the history loads.
    const unsubscribe = subscribe<RuntimeLogLine>(
      `/topic/environments/${environmentId}/logs`,
      (line) => (loaded ? append([line]) : buffered.push(line)),
    );
    monitoringApi
      .runtimeLogs(environmentId)
      .then((history) => {
        if (!cancelled) {
          setLines([...history, ...buffered].slice(-MAX_LOG_LINES));
          loaded = true;
        }
      })
      .catch((e) => !cancelled && setError(e));
    return () => {
      cancelled = true;
      unsubscribe();
    };
  }, [environmentId]);

  return { lines, error };
}
