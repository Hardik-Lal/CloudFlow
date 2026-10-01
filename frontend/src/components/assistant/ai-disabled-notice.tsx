import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";

export function AiDisabledNotice() {
  return (
    <Alert>
      <AlertTitle>AI features are not configured</AlertTitle>
      <AlertDescription>
        The administrator needs to set <code>OPENAI_API_KEY</code> and{" "}
        <code>CLOUDFLOW_AI_INTERNAL_TOKEN</code> for this CloudFlow instance. Everything else keeps
        working without AI.
      </AlertDescription>
    </Alert>
  );
}
