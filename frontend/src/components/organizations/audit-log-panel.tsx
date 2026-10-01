"use client";

import { useState } from "react";
import { QueryState } from "@/components/query-state";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { useAuditLogs } from "@/hooks/use-organizations";
import { formatDateTime } from "@/lib/format";

const ACTIONS = [
  "USER_SIGNED_IN",
  "ORGANIZATION_CREATED",
  "ORGANIZATION_UPDATED",
  "MEMBER_ADDED",
  "MEMBER_ROLE_CHANGED",
  "MEMBER_REMOVED",
  "PROJECT_CREATED",
  "PROJECT_UPDATED",
  "PROJECT_DELETED",
  "ENVIRONMENT_CREATED",
  "ENVIRONMENT_UPDATED",
  "ENVIRONMENT_DELETED",
  "VARIABLE_SET",
  "VARIABLE_DELETED",
  "DEPLOYMENT_CONFIG_UPDATED",
  "DEPLOYMENT_TRIGGERED",
  "DEPLOYMENT_ROLLBACK_TRIGGERED",
  "DEPLOYMENT_CANCELLED",
  "PIPELINE_COMMITTED",
  "PIPELINE_DELETED",
  "DEPLOY_TOKEN_CREATED",
  "DEPLOY_TOKEN_REVOKED",
  "AI_SUGGESTION_APPLIED",
  "AI_SUGGESTION_REJECTED",
];

const label = (action: string) => action.toLowerCase().replaceAll("_", " ");

/** Organization audit trail: who did what, when, from where (never secret values). */
export function AuditLogPanel({ organizationId }: { organizationId: string }) {
  const [page, setPage] = useState(0);
  const [action, setAction] = useState("");
  const logs = useAuditLogs(organizationId, page, action);
  const items: Record<string, string> = {
    all: "All actions",
    ...Object.fromEntries(ACTIONS.map((a) => [a, label(a)])),
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2">
        <Select
          items={items}
          value={action || "all"}
          onValueChange={(value) => {
            setAction(!value || value === "all" ? "" : value);
            setPage(0);
          }}
        >
          <SelectTrigger className="w-64" aria-label="Filter by action">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {Object.entries(items).map(([value, text]) => (
              <SelectItem key={value} value={value}>
                {text}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
      <QueryState
        isPending={logs.isPending}
        error={logs.error}
        errorTitle="Could not load the audit log"
      >
        {() =>
          logs.data && (
            <div className={logs.isFetching ? "opacity-60 transition-opacity" : undefined}>
              <div className="rounded-xl border">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>When</TableHead>
                      <TableHead>Who</TableHead>
                      <TableHead>Action</TableHead>
                      <TableHead>Details</TableHead>
                      <TableHead>IP</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {logs.data.content.map((entry) => (
                      <TableRow key={entry.id}>
                        <TableCell className="text-xs whitespace-nowrap text-muted-foreground">
                          {formatDateTime(entry.createdAt)}
                        </TableCell>
                        <TableCell className="text-sm">{entry.actorUsername ?? "—"}</TableCell>
                        <TableCell className="text-sm font-medium">{label(entry.action)}</TableCell>
                        <TableCell className="max-w-md text-xs text-muted-foreground">
                          {Object.entries(entry.details)
                            .map(([key, value]) => `${key}: ${value}`)
                            .join(" · ")}
                        </TableCell>
                        <TableCell className="text-xs text-muted-foreground">
                          {entry.ipAddress ?? "—"}
                        </TableCell>
                      </TableRow>
                    ))}
                    {logs.data.content.length === 0 && (
                      <TableRow>
                        <TableCell colSpan={5} className="py-6 text-center text-muted-foreground">
                          No audit entries.
                        </TableCell>
                      </TableRow>
                    )}
                  </TableBody>
                </Table>
              </div>
              <div className="mt-3 flex items-center justify-between text-sm text-muted-foreground">
                <span>
                  Page {logs.data.page + 1} of {Math.max(logs.data.totalPages, 1)} ·{" "}
                  {logs.data.totalElements} entries
                </span>
                <div className="flex gap-2">
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={page === 0}
                    onClick={() => setPage(page - 1)}
                  >
                    Previous
                  </Button>
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={page + 1 >= logs.data.totalPages}
                    onClick={() => setPage(page + 1)}
                  >
                    Next
                  </Button>
                </div>
              </div>
            </div>
          )
        }
      </QueryState>
    </div>
  );
}
