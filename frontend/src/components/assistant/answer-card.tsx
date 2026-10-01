import { ListChecksIcon, SearchIcon, SparklesIcon } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import type { AssistantAnswer, Confidence } from "@/lib/api/types";

const CONFIDENCE_LABELS: Record<Confidence, string> = {
  HIGH: "High confidence",
  MEDIUM: "Medium confidence",
  LOW: "Low confidence",
};

/** An AI answer with its likely cause, supporting evidence, actions, and stated uncertainty. */
export function AnswerCard({ answer, question }: { answer: AssistantAnswer; question?: string }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex flex-wrap items-center gap-2 text-base">
          <SparklesIcon className="size-4" />
          {question ?? "Analysis"}
          <Badge variant={answer.confidence === "LOW" ? "outline" : "secondary"}>
            {CONFIDENCE_LABELS[answer.confidence]}
          </Badge>
        </CardTitle>
      </CardHeader>
      <CardContent className="grid gap-4 text-sm">
        {answer.likelyCause && (
          <div className="rounded-lg border-l-4 border-amber-500 bg-muted/40 px-3 py-2">
            <div className="text-xs font-medium text-muted-foreground">Likely cause</div>
            <div className="font-medium">{answer.likelyCause}</div>
          </div>
        )}
        <p className="whitespace-pre-wrap">{answer.answer}</p>
        {answer.recommendedActions.length > 0 && (
          <div>
            <div className="mb-1 flex items-center gap-1.5 font-medium">
              <ListChecksIcon className="size-4" />
              Recommended actions
            </div>
            <ol className="list-decimal space-y-1 pl-5">
              {answer.recommendedActions.map((action) => (
                <li key={action}>{action}</li>
              ))}
            </ol>
          </div>
        )}
        {answer.evidence.length > 0 && (
          <div>
            <div className="mb-1 flex items-center gap-1.5 font-medium">
              <SearchIcon className="size-4" />
              Evidence
            </div>
            <ul className="grid gap-2">
              {answer.evidence.map((item, index) => (
                <li key={index} className="rounded-lg border p-2">
                  <div className="text-xs text-muted-foreground">{item.reference}</div>
                  <pre className="mt-1 overflow-x-auto font-mono text-xs whitespace-pre-wrap">
                    {item.excerpt}
                  </pre>
                </li>
              ))}
            </ul>
          </div>
        )}
        <p className="text-xs text-muted-foreground">{answer.confidenceExplanation}</p>
      </CardContent>
    </Card>
  );
}
