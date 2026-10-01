"use client";

import Link from "next/link";
import { RoleBadge } from "@/components/role-badge";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Card, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { useOrganizations } from "@/hooks/use-organizations";
import { errorMessage } from "@/lib/api/errors";

export function OrganizationList() {
  const { data: organizations, isPending, error } = useOrganizations();

  if (isPending) {
    return (
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {Array.from({ length: 3 }, (_, index) => (
          <Skeleton key={index} className="h-24" />
        ))}
      </div>
    );
  }
  if (error) {
    return (
      <Alert variant="destructive">
        <AlertTitle>Could not load organizations</AlertTitle>
        <AlertDescription>{errorMessage(error)}</AlertDescription>
      </Alert>
    );
  }
  if (organizations.length === 0) {
    return (
      <div className="rounded-xl border border-dashed p-10 text-center text-sm text-muted-foreground">
        You are not a member of any organization yet. Create one to get started, or ask an
        organization admin to add you by your GitHub username.
      </div>
    );
  }

  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {organizations.map((organization) => (
        <Link key={organization.id} href={`/organizations/${organization.id}`} className="group">
          <Card className="h-full transition-colors group-hover:border-foreground/20">
            <CardHeader>
              <div className="flex items-start justify-between gap-2">
                <CardTitle>{organization.name}</CardTitle>
                <RoleBadge role={organization.role} />
              </div>
              <CardDescription>{organization.slug}</CardDescription>
            </CardHeader>
          </Card>
        </Link>
      ))}
    </div>
  );
}
