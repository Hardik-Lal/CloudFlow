"use client";

import { WandSparklesIcon } from "lucide-react";
import { useState, type FormEvent } from "react";
import { toast } from "sonner";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { QueryState } from "@/components/query-state";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useGenerateSuggestion, useReviewSuggestion, useSuggestions } from "@/hooks/use-assistant";
import { useEnvironments } from "@/hooks/use-environments";
import { errorMessage } from "@/lib/api/errors";
import type { Project, Suggestion, SuggestionType } from "@/lib/api/types";
import { ENVIRONMENT_LABELS } from "@/lib/environments";
import { formatRelativeTime } from "@/lib/format";

const TYPE_LABELS: Record<SuggestionType, string> = {
  DOCKERFILE: "Dockerfile",
  ENV_TEMPLATE: "Environment variables",
  WORKFLOW: "CI workflow",
  DOCUMENTATION: "Documentation",
};

/** AI-generated files and variables; nothing is changed until a user applies a suggestion. */
export function SuggestionsPanel({ project }: { project: Project }) {
  const suggestions = useSuggestions(project.id);
  const canGenerate = project.permissions.includes("AI_USE");

  return (
    <Card>
      <CardHeader>
        <CardTitle>AI suggestions</CardTitle>
        <CardDescription>
          Generated for review. Applying commits the file to the repository or creates the
          variables; rejecting discards it.
        </CardDescription>
      </CardHeader>
      <CardContent className="grid gap-4">
        {canGenerate && <GenerateForm project={project} />}
        <QueryState
          isPending={suggestions.isPending}
          error={suggestions.error}
          skeletonClassName="h-24"
        >
          {() =>
            suggestions.data && suggestions.data.length > 0 ? (
              <ul className="grid gap-3">
                {suggestions.data.map((suggestion) => (
                  <SuggestionItem key={suggestion.id} project={project} suggestion={suggestion} />
                ))}
              </ul>
            ) : (
              <p className="text-sm text-muted-foreground">No suggestions yet.</p>
            )
          }
        </QueryState>
      </CardContent>
    </Card>
  );
}

function GenerateForm({ project }: { project: Project }) {
  const [type, setType] = useState<SuggestionType>("DOCKERFILE");
  const [environmentId, setEnvironmentId] = useState<string>("none");
  const [instructions, setInstructions] = useState("");
  const environments = useEnvironments(project.id);
  const generate = useGenerateSuggestion(project.id);
  const needsEnvironment = type === "ENV_TEMPLATE";

  const environmentItems: Record<string, string> = {
    none: "No environment",
    ...Object.fromEntries(
      (environments.data ?? []).map((env) => [env.id, ENVIRONMENT_LABELS[env.type]]),
    ),
  };

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    generate.mutate(
      {
        type,
        environmentId: environmentId === "none" ? undefined : environmentId,
        instructions: instructions.trim() || undefined,
      },
      {
        onSuccess: () => {
          toast.success(`${TYPE_LABELS[type]} generated for review`);
          setInstructions("");
        },
        onError: (error) => toast.error(errorMessage(error)),
      },
    );
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-wrap items-center gap-2">
      <Select
        items={TYPE_LABELS}
        value={type}
        onValueChange={(value) => value && setType(value as SuggestionType)}
      >
        <SelectTrigger className="w-48" aria-label="What to generate">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {(Object.keys(TYPE_LABELS) as SuggestionType[]).map((value) => (
            <SelectItem key={value} value={value}>
              {TYPE_LABELS[value]}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      <Select
        items={environmentItems}
        value={environmentId}
        onValueChange={(value) => value && setEnvironmentId(value)}
      >
        <SelectTrigger className="w-44" aria-label="Environment">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {Object.entries(environmentItems).map(([value, label]) => (
            <SelectItem key={value} value={value}>
              {label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      <Input
        aria-label="Instructions"
        placeholder="Optional instructions"
        className="min-w-48 flex-1"
        value={instructions}
        onChange={(event) => setInstructions(event.target.value)}
        maxLength={2000}
      />
      <Button
        type="submit"
        disabled={generate.isPending || (needsEnvironment && environmentId === "none")}
      >
        <WandSparklesIcon />
        {generate.isPending ? "Generating…" : "Generate"}
      </Button>
    </form>
  );
}

function SuggestionItem({ project, suggestion }: { project: Project; suggestion: Suggestion }) {
  const review = useReviewSuggestion(project.id);
  const canApply = project.permissions.includes("AI_APPLY");
  const pending = suggestion.status === "PENDING";

  function decide(decision: "apply" | "reject") {
    review.mutate(
      { id: suggestion.id, decision },
      {
        onSuccess: (result) =>
          toast.success(
            decision === "apply" ? (result.result ?? "Applied") : "Suggestion rejected",
          ),
        onError: (error) => toast.error(errorMessage(error)),
      },
    );
  }

  return (
    <li className="rounded-lg border p-3 text-sm">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <Badge variant="outline">{TYPE_LABELS[suggestion.type]}</Badge>
          <code className="text-xs">{suggestion.filePath}</code>
          <Badge
            variant={
              pending ? "secondary" : suggestion.status === "APPLIED" ? "default" : "outline"
            }
          >
            {suggestion.status.toLowerCase()}
          </Badge>
        </div>
        <span className="text-xs text-muted-foreground">
          {formatRelativeTime(suggestion.createdAt)}
        </span>
      </div>
      {suggestion.explanation && (
        <p className="mt-2 text-muted-foreground">{suggestion.explanation}</p>
      )}
      <pre className="mt-2 max-h-64 overflow-auto rounded-md bg-zinc-950 p-3 font-mono text-xs text-zinc-100">
        {suggestion.content}
      </pre>
      {suggestion.result && <p className="mt-2 text-xs">{suggestion.result}</p>}
      {pending && (
        <div className="mt-2 flex gap-2">
          {canApply && (
            <ConfirmDialog
              trigger={
                <Button size="sm" disabled={review.isPending}>
                  Apply
                </Button>
              }
              title={
                suggestion.type === "ENV_TEMPLATE"
                  ? "Create these variables?"
                  : `Commit ${suggestion.filePath} to the repository?`
              }
              description={
                suggestion.type === "ENV_TEMPLATE"
                  ? "Missing variables are created; existing ones are kept. Secrets are listed for you to set."
                  : "CloudFlow commits the file shown above with your GitHub account."
              }
              confirmLabel="Apply"
              destructive={false}
              onConfirm={() => decide("apply")}
            />
          )}
          <Button
            size="sm"
            variant="ghost"
            disabled={review.isPending}
            onClick={() => decide("reject")}
          >
            Reject
          </Button>
        </div>
      )}
    </li>
  );
}
