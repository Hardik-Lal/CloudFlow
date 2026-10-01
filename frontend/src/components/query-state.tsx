import type { ReactNode } from "react";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Skeleton } from "@/components/ui/skeleton";
import { errorMessage } from "@/lib/api/errors";

interface QueryStateProps {
  isPending: boolean;
  error: unknown;
  errorTitle?: string;
  skeletonClassName?: string;
  children: () => ReactNode;
}

/** Standard loading / error rendering for a TanStack Query result. */
export function QueryState({
  isPending,
  error,
  errorTitle = "Could not load data",
  skeletonClassName = "h-32",
  children,
}: QueryStateProps) {
  if (error) {
    return (
      <Alert variant="destructive">
        <AlertTitle>{errorTitle}</AlertTitle>
        <AlertDescription>{errorMessage(error)}</AlertDescription>
      </Alert>
    );
  }
  if (isPending) {
    return <Skeleton className={skeletonClassName} />;
  }
  return <>{children()}</>;
}
