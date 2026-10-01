"use client";

import { CircleXIcon, InfoIcon, TriangleAlertIcon } from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";
import { TimeSeriesChart, type TimePoint } from "@/components/charts/time-series-chart";
import { QueryState } from "@/components/query-state";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { useEnvironmentEvents, useEnvironmentMetrics } from "@/hooks/use-monitoring";
import type { EnvironmentMetrics, MetricSample } from "@/lib/api/types";
import { formatRelativeTime } from "@/lib/format";
import { formatBytes, formatPercent, formatUptime } from "@/lib/units";
import { ServiceStatusBadge } from "./service-status-badge";
import { StatTile } from "./stat-tile";

export function MonitoringPanel({ environmentId }: { environmentId: string }) {
  const metrics = useEnvironmentMetrics(environmentId);
  return (
    <div className="space-y-6">
      <QueryState
        isPending={metrics.isPending}
        error={metrics.error}
        errorTitle="Could not load metrics"
        skeletonClassName="h-64"
      >
        {() => metrics.data && <MetricsView metrics={metrics.data} />}
      </QueryState>
      <EventsCard environmentId={environmentId} />
    </div>
  );
}

function MetricsView({ metrics }: { metrics: EnvironmentMetrics }) {
  if (metrics.status === "NOT_DEPLOYED") {
    return (
      <div className="flex items-center gap-3 rounded-xl border border-dashed p-6 text-sm text-muted-foreground">
        <ServiceStatusBadge status="NOT_DEPLOYED" />
        Deploy the environment to start collecting metrics.
      </div>
    );
  }
  const { current, health, history } = metrics;
  const series = (pick: (sample: MetricSample) => number | null): TimePoint[] =>
    history.map((sample) => ({ time: Date.parse(sample.timestamp), value: pick(sample) }));

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3 text-sm">
        <ServiceStatusBadge status={metrics.status} />
        {metrics.deploymentId && (
          <Link
            href={`/deployments/${metrics.deploymentId}`}
            className="text-muted-foreground hover:underline"
          >
            Deployment {metrics.deploymentId.slice(0, 8)}
          </Link>
        )}
        {current && (
          <span className="text-muted-foreground">
            sampled {formatRelativeTime(current.timestamp)}
          </span>
        )}
      </div>
      <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
        <StatTile
          label="Uptime (health checks, 1h)"
          value={formatPercent(health.uptimePercent)}
          detail={`${health.checks} checks`}
        />
        <StatTile
          label="Error rate (1h)"
          value={formatPercent(health.errorRatePercent)}
          detail="Failed or unreachable probes"
        />
        <StatTile
          label="Response time"
          value={current?.responseTimeMs != null ? `${current.responseTimeMs} ms` : "—"}
          detail={
            health.averageResponseTimeMs != null
              ? `avg ${Math.round(health.averageResponseTimeMs)} ms · p95 ${health.p95ResponseTimeMs} ms`
              : undefined
          }
        />
        <StatTile
          label="Container uptime"
          value={current ? formatUptime(current.uptimeSeconds) : "—"}
          detail={current ? `${current.restartCount} restarts` : undefined}
        />
        <StatTile label="CPU" value={current ? formatPercent(current.cpuPercent) : "—"} />
        <StatTile
          label="Memory"
          value={current ? formatBytes(current.memoryBytes) : "—"}
          detail={
            current?.memoryLimitBytes ? `of ${formatBytes(current.memoryLimitBytes)}` : undefined
          }
        />
        <StatTile label="Network in" value={current ? formatBytes(current.networkRxBytes) : "—"} />
        <StatTile label="Network out" value={current ? formatBytes(current.networkTxBytes) : "—"} />
      </div>
      <div className="grid gap-4 lg:grid-cols-3">
        <ChartCard title="CPU (%)">
          <TimeSeriesChart
            label="CPU usage in percent"
            points={series((s) => (s.running ? s.cpuPercent : null))}
            formatValue={(v) => `${v.toFixed(v < 1 ? 2 : v < 10 ? 1 : 0)}%`}
            minTop={1}
          />
        </ChartCard>
        <ChartCard title="Memory">
          <TimeSeriesChart
            label="Memory usage"
            points={series((s) => (s.running ? s.memoryBytes : null))}
            formatValue={formatBytes}
          />
        </ChartCard>
        <ChartCard title="Response time (ms)">
          <TimeSeriesChart
            label="Health-check response time in milliseconds"
            points={series((s) => s.responseTimeMs)}
            formatValue={(v) => `${Math.round(v)} ms`}
            minTop={10}
          />
        </ChartCard>
      </div>
    </div>
  );
}

function ChartCard({ title, children }: { title: string; children: ReactNode }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-sm">{title}</CardTitle>
      </CardHeader>
      <CardContent>{children}</CardContent>
    </Card>
  );
}

const SEVERITY_ICONS = {
  INFO: <InfoIcon className="size-4 text-muted-foreground" aria-label="Info" />,
  WARN: <TriangleAlertIcon className="size-4 text-amber-500" aria-label="Warning" />,
  ERROR: <CircleXIcon className="size-4 text-destructive" aria-label="Error" />,
};

function EventsCard({ environmentId }: { environmentId: string }) {
  const events = useEnvironmentEvents(environmentId);
  return (
    <Card>
      <CardHeader>
        <CardTitle>Recent events</CardTitle>
      </CardHeader>
      <CardContent>
        <QueryState
          isPending={events.isPending}
          error={events.error}
          errorTitle="Could not load events"
        >
          {() => (
            <ul className="divide-y text-sm">
              {events.data?.map((event) => (
                <li key={event.id} className="flex items-start gap-3 py-2">
                  <span className="mt-0.5">{SEVERITY_ICONS[event.severity]}</span>
                  <div className="min-w-0 flex-1">
                    <div>{event.message}</div>
                    <div className="text-xs text-muted-foreground">
                      {event.type.toLowerCase().replaceAll("_", " ")} ·{" "}
                      {formatRelativeTime(event.createdAt)}
                    </div>
                  </div>
                </li>
              ))}
              {events.data?.length === 0 && (
                <li className="py-4 text-muted-foreground">No events yet.</li>
              )}
            </ul>
          )}
        </QueryState>
      </CardContent>
    </Card>
  );
}
