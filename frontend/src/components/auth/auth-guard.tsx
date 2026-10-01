"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { refreshSession } from "@/lib/api/client";
import { useAuthStore } from "@/stores/auth-store";

/**
 * Renders children only for signed-in users. On first load the access token is recovered from the
 * refresh cookie; if that fails the user is sent to the login page and returned here afterwards.
 */
export function AuthGuard({ children }: { children: ReactNode }) {
  const status = useAuthStore((state) => state.status);
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    if (status === "unknown") {
      refreshSession().catch(() => undefined);
    } else if (status === "anonymous") {
      router.replace(`/login?returnTo=${encodeURIComponent(pathname)}`);
    }
  }, [status, router, pathname]);

  if (status !== "authenticated") {
    return (
      <div className="mx-auto w-full max-w-5xl space-y-4 p-6" aria-busy="true">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-32 w-full" />
      </div>
    );
  }
  return children;
}
