"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { toast } from "sonner";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { FormField } from "@/components/form-field";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { useDeleteOrganization, useRenameOrganization } from "@/hooks/use-organizations";
import { errorMessage } from "@/lib/api/errors";
import type { Organization } from "@/lib/api/types";
import { useOrganizationStore } from "@/stores/organization-store";

export function OrganizationSettings({ organization }: { organization: Organization }) {
  const canUpdate = organization.permissions.includes("ORG_UPDATE");
  const canDelete = organization.permissions.includes("ORG_DELETE");

  return (
    <div className="grid max-w-2xl gap-6">
      <RenameCard organization={organization} disabled={!canUpdate} />
      {canDelete && <DeleteCard organization={organization} />}
    </div>
  );
}

function RenameCard({ organization, disabled }: { organization: Organization; disabled: boolean }) {
  const [name, setName] = useState(organization.name);
  const rename = useRenameOrganization(organization.id);

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    rename.mutate(name.trim(), {
      onSuccess: () => toast.success("Organization renamed"),
      onError: (error) => toast.error(errorMessage(error)),
    });
  }

  return (
    <Card>
      <form onSubmit={handleSubmit}>
        <CardHeader>
          <CardTitle>General</CardTitle>
          <CardDescription>The slug ({organization.slug}) cannot be changed.</CardDescription>
        </CardHeader>
        <CardContent className="py-4">
          <FormField id="rename-org" label="Organization name">
            <Input
              id="rename-org"
              value={name}
              onChange={(event) => setName(event.target.value)}
              maxLength={100}
              disabled={disabled}
              required
            />
          </FormField>
        </CardContent>
        <CardFooter>
          <Button
            type="submit"
            disabled={disabled || rename.isPending || !name.trim() || name === organization.name}
          >
            Save
          </Button>
        </CardFooter>
      </form>
    </Card>
  );
}

function DeleteCard({ organization }: { organization: Organization }) {
  const deleteOrganization = useDeleteOrganization(organization.id);
  const selectOrganization = useOrganizationStore((state) => state.selectOrganization);
  const router = useRouter();

  function handleDelete() {
    deleteOrganization.mutate(undefined, {
      onSuccess: () => {
        toast.success(`${organization.name} deleted`);
        selectOrganization(null);
        router.replace("/organizations");
      },
      onError: (error) => toast.error(errorMessage(error)),
    });
  }

  return (
    <Card className="border-destructive/40">
      <CardHeader>
        <CardTitle>Delete organization</CardTitle>
        <CardDescription>
          Permanently deletes the organization and removes every member. This cannot be undone.
        </CardDescription>
      </CardHeader>
      <CardFooter>
        <ConfirmDialog
          trigger={
            <Button variant="destructive" disabled={deleteOrganization.isPending}>
              Delete organization
            </Button>
          }
          title={`Delete ${organization.name}?`}
          description="All members lose access immediately. This action cannot be undone."
          confirmLabel="Delete"
          onConfirm={handleDelete}
        />
      </CardFooter>
    </Card>
  );
}
