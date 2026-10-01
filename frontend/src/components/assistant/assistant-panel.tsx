"use client";

import { DatabaseIcon, RefreshCwIcon, SendIcon } from "lucide-react";
import { useState, type FormEvent } from "react";
import { toast } from "sonner";
import { QueryState } from "@/components/query-state";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { useAskAssistant, useKnowledgeStatus, useReindexKnowledge } from "@/hooks/use-assistant";
import { useEnvironments } from "@/hooks/use-environments";
import { errorMessage } from "@/lib/api/errors";
import type { AssistantAnswer, Project } from "@/lib/api/types";
import { ENVIRONMENT_LABELS } from "@/lib/environments";
import { formatRelativeTime } from "@/lib/format";
import { AiDisabledNotice } from "./ai-disabled-notice";
import { AnswerCard } from "./answer-card";
import { SuggestionsPanel } from "./suggestions-panel";

const EXAMPLES = [
  "Why did my latest deployment fail?",
  "Which environment variables does this app need?",
  "How is this project built and started?",
];

export function AssistantPanel({ project }: { project: Project }) {
  const knowledge = useKnowledgeStatus(project.id);
  const canUse = project.permissions.includes("AI_USE");

  return (
    <QueryState isPending={knowledge.isPending} error={knowledge.error} skeletonClassName="h-40">
      {() =>
        knowledge.data && !knowledge.data.enabled ? (
          <AiDisabledNotice />
        ) : (
          <div className="grid gap-6 lg:grid-cols-3">
            <div className="space-y-6 lg:col-span-2">
              {canUse && <AskCard project={project} />}
              <SuggestionsPanel project={project} />
            </div>
            <KnowledgeCard project={project} canReindex={canUse} />
          </div>
        )
      }
    </QueryState>
  );
}

function AskCard({ project }: { project: Project }) {
  const [question, setQuestion] = useState("");
  const [environmentId, setEnvironmentId] = useState<string>("all");
  const [history, setHistory] = useState<{ question: string; answer: AssistantAnswer }[]>([]);
  const environments = useEnvironments(project.id);
  const ask = useAskAssistant(project.id);

  function submit(text: string) {
    ask.mutate(
      { question: text, environmentId: environmentId === "all" ? undefined : environmentId },
      {
        onSuccess: (answer) => {
          setHistory((current) => [{ question: text, answer }, ...current]);
          setQuestion("");
        },
        onError: (error) => toast.error(errorMessage(error)),
      },
    );
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (question.trim().length >= 3) {
      submit(question.trim());
    }
  }

  const environmentItems: Record<string, string> = {
    all: "All environments",
    ...Object.fromEntries(
      (environments.data ?? []).map((env) => [env.id, ENVIRONMENT_LABELS[env.type]]),
    ),
  };

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader>
          <CardTitle>Ask about this project</CardTitle>
          <CardDescription>
            Answers use this project&apos;s indexed repository files, configuration, deployment
            history, and logs, plus its live status, and cite their evidence.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="grid gap-3">
            <Textarea
              aria-label="Question"
              placeholder="Why did my latest deployment fail?"
              value={question}
              onChange={(event) => setQuestion(event.target.value)}
              maxLength={2000}
              rows={3}
            />
            <div className="flex flex-wrap items-center gap-2">
              <Select
                items={environmentItems}
                value={environmentId}
                onValueChange={(value) => value && setEnvironmentId(value)}
              >
                <SelectTrigger className="w-48" aria-label="Environment scope">
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
              <Button type="submit" disabled={ask.isPending || question.trim().length < 3}>
                <SendIcon />
                {ask.isPending ? "Thinking…" : "Ask"}
              </Button>
            </div>
            <div className="flex flex-wrap gap-2">
              {EXAMPLES.map((example) => (
                <Button
                  key={example}
                  type="button"
                  size="xs"
                  variant="outline"
                  disabled={ask.isPending}
                  onClick={() => submit(example)}
                >
                  {example}
                </Button>
              ))}
            </div>
          </form>
        </CardContent>
      </Card>
      {history.map((entry, index) => (
        <AnswerCard key={history.length - index} question={entry.question} answer={entry.answer} />
      ))}
    </div>
  );
}

function KnowledgeCard({ project, canReindex }: { project: Project; canReindex: boolean }) {
  const knowledge = useKnowledgeStatus(project.id);
  const reindex = useReindexKnowledge(project.id);
  const status = knowledge.data;

  return (
    <Card className="h-fit">
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <DatabaseIcon className="size-4" />
          Knowledge base
        </CardTitle>
        <CardDescription>
          Deployments are indexed automatically when they finish. Re-index after changing the
          repository or configuration.
        </CardDescription>
      </CardHeader>
      <CardContent className="grid gap-3 text-sm">
        <dl className="grid grid-cols-2 gap-2">
          <dt className="text-muted-foreground">Documents</dt>
          <dd className="tabular-nums">{status?.documents ?? 0}</dd>
          <dt className="text-muted-foreground">Chunks</dt>
          <dd className="tabular-nums">{status?.chunks ?? 0}</dd>
          <dt className="text-muted-foreground">Last indexed</dt>
          <dd>{status?.lastIndexedAt ? formatRelativeTime(status.lastIndexedAt) : "never"}</dd>
        </dl>
        {canReindex && (
          <Button
            variant="outline"
            disabled={reindex.isPending}
            onClick={() =>
              reindex.mutate(undefined, {
                onSuccess: (result) =>
                  toast.success(
                    `Indexed ${result.indexed} of ${result.collected} sources (${result.unchanged} unchanged)`,
                  ),
                onError: (error) => toast.error(errorMessage(error)),
              })
            }
          >
            <RefreshCwIcon className={reindex.isPending ? "animate-spin" : undefined} />
            {reindex.isPending ? "Indexing…" : "Re-index project"}
          </Button>
        )}
      </CardContent>
    </Card>
  );
}
