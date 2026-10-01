"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Input } from "@/components/ui/input";
import { useRuntimeLogs } from "@/hooks/use-monitoring";
import { errorMessage } from "@/lib/api/errors";

/** Terminal-style live output of the environment's running container. */
export function LiveLogsPanel({ environmentId }: { environmentId: string }) {
  const { lines, error } = useRuntimeLogs(environmentId);
  const [query, setQuery] = useState("");
  const [follow, setFollow] = useState(true);
  const container = useRef<HTMLDivElement>(null);

  const visible = useMemo(() => {
    const term = query.trim().toLowerCase();
    return term ? lines.filter((line) => line.message.toLowerCase().includes(term)) : lines;
  }, [lines, query]);

  useEffect(() => {
    if (follow && container.current) {
      container.current.scrollTop = container.current.scrollHeight;
    }
  }, [visible, follow]);

  return (
    <div className="space-y-2">
      <div className="flex flex-wrap items-center gap-2">
        <span className="flex items-center gap-1.5 text-sm text-muted-foreground">
          <span className="size-2 animate-pulse rounded-full bg-emerald-500" aria-hidden />
          Live
        </span>
        <Input
          aria-label="Filter log lines"
          placeholder="Filter"
          className="h-7 max-w-64"
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
        role="log"
        aria-live="polite"
        className="h-[32rem] overflow-y-auto rounded-lg bg-zinc-950 p-3 font-mono text-xs leading-5 text-zinc-100"
      >
        {visible.map((line, index) => (
          <div key={index} className="break-all whitespace-pre-wrap">
            {line.message}
          </div>
        ))}
        {visible.length === 0 && (
          <div className="text-zinc-500">
            {lines.length === 0
              ? "Waiting for output from the running container…"
              : "No matching lines."}
          </div>
        )}
      </div>
    </div>
  );
}
