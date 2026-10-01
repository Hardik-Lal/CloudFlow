"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { errorMessage } from "@/lib/api/errors";
import type { DeploymentLogLine, LogPhase } from "@/lib/api/types";
import { cn } from "@/lib/utils";

const PHASES: (LogPhase | "ALL")[] = [
  "ALL",
  "SOURCE",
  "BUILD",
  "SCAN",
  "PUSH",
  "DEPLOY",
  "HEALTH_CHECK",
  "RUNTIME",
];

interface LogViewerProps {
  lines: DeploymentLogLine[];
  error?: unknown;
  live: boolean;
}

/** Terminal-style log viewer with phase filter, text search, and follow-tail scrolling. */
export function LogViewer({ lines, error, live }: LogViewerProps) {
  const [phase, setPhase] = useState<LogPhase | "ALL">("ALL");
  const [query, setQuery] = useState("");
  const [follow, setFollow] = useState(true);
  const container = useRef<HTMLDivElement>(null);

  const visible = useMemo(() => {
    const term = query.trim().toLowerCase();
    return lines.filter(
      (line) =>
        (phase === "ALL" || line.phase === phase) &&
        (!term || line.message.toLowerCase().includes(term)),
    );
  }, [lines, phase, query]);

  useEffect(() => {
    if (follow && container.current) {
      container.current.scrollTop = container.current.scrollHeight;
    }
  }, [visible, follow]);

  return (
    <div className="space-y-2">
      <div className="flex flex-wrap items-center gap-2">
        <div className="flex flex-wrap gap-1" role="group" aria-label="Filter by phase">
          {PHASES.map((option) => (
            <Button
              key={option}
              size="xs"
              variant={phase === option ? "default" : "outline"}
              onClick={() => setPhase(option)}
            >
              {option === "ALL" ? "All" : option.replace("_", " ").toLowerCase()}
            </Button>
          ))}
        </div>
        <Input
          aria-label="Search logs"
          placeholder="Search logs"
          className="h-7 max-w-56"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
        />
        <label className="ml-auto flex items-center gap-1.5 text-xs text-muted-foreground">
          <input type="checkbox" checked={follow} onChange={(e) => setFollow(e.target.checked)} />
          Follow
        </label>
      </div>
      {error ? (
        <Alert variant="destructive">
          <AlertDescription>{errorMessage(error)}</AlertDescription>
        </Alert>
      ) : null}
      <div
        ref={container}
        className="h-[28rem] overflow-y-auto rounded-lg bg-zinc-950 p-3 font-mono text-xs leading-5 text-zinc-100"
        role="log"
        aria-live={live ? "polite" : "off"}
      >
        {visible.map((line) => (
          <div key={line.id} className="flex gap-3 break-all whitespace-pre-wrap">
            <span className="shrink-0 text-zinc-500 select-none">
              {new Date(line.loggedAt).toLocaleTimeString()}
            </span>
            <span className="w-24 shrink-0 text-zinc-400 select-none">{line.phase}</span>
            <span
              className={cn(
                line.level === "ERROR" && "text-red-400",
                line.level === "WARN" && "text-amber-300",
              )}
            >
              {line.message}
            </span>
          </div>
        ))}
        {visible.length === 0 && (
          <div className="text-zinc-500">{live ? "Waiting for output…" : "No log lines."}</div>
        )}
        {live && visible.length > 0 && <div className="animate-pulse text-zinc-500">▍</div>}
      </div>
    </div>
  );
}
