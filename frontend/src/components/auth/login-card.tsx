"use client";

import { useSearchParams } from "next/navigation";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { GithubSignInButton } from "./github-sign-in-button";
import { rememberReturnTo } from "./return-to";

const ERROR_MESSAGES: Record<string, string> = {
  oauth_failed: "GitHub sign-in did not complete. Please try again.",
  session_expired: "Your session has expired. Please sign in again.",
};

export function LoginCard() {
  const searchParams = useSearchParams();
  const error = searchParams.get("error");
  const returnTo = searchParams.get("returnTo");

  return (
    <Card className="w-full max-w-sm">
      <CardHeader>
        <CardTitle className="text-xl">Sign in to CloudFlow</CardTitle>
        <CardDescription>
          Use your GitHub account. CloudFlow uses it to access the repositories you deploy.
        </CardDescription>
      </CardHeader>
      <CardContent className="grid gap-4">
        {error && (
          <Alert variant="destructive">
            <AlertDescription>{ERROR_MESSAGES[error] ?? "Sign-in failed."}</AlertDescription>
          </Alert>
        )}
        <div onClickCapture={() => rememberReturnTo(returnTo)}>
          <GithubSignInButton className="w-full" />
        </div>
      </CardContent>
    </Card>
  );
}
