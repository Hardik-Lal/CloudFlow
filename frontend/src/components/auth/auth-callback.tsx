"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { refreshSession } from "@/lib/api/client";
import { consumeReturnTo } from "./return-to";

/** Landing page after GitHub OAuth: exchanges the new refresh cookie for an access token. */
export function AuthCallback() {
  const router = useRouter();

  useEffect(() => {
    refreshSession()
      .then(() => router.replace(consumeReturnTo()))
      .catch(() => router.replace("/login?error=oauth_failed"));
  }, [router]);

  return (
    <main className="flex flex-1 items-center justify-center text-sm text-muted-foreground">
      Signing you in…
    </main>
  );
}
