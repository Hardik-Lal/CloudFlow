"use client";

import { PlusIcon } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { toast } from "sonner";
import { FormField } from "@/components/form-field";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { useCreateOrganization } from "@/hooks/use-organizations";
import { ApiError, errorMessage } from "@/lib/api/errors";
import { useOrganizationStore } from "@/stores/organization-store";

export function CreateOrganizationDialog() {
  const [open, setOpen] = useState(false);
  const [name, setName] = useState("");
  const [slug, setSlug] = useState("");
  const createOrganization = useCreateOrganization();
  const selectOrganization = useOrganizationStore((state) => state.selectOrganization);
  const router = useRouter();

  const fieldError = (field: string) =>
    createOrganization.error instanceof ApiError
      ? createOrganization.error.fieldErrors.find((e) => e.field === field)?.message
      : undefined;

  function handleOpenChange(next: boolean) {
    setOpen(next);
    if (!next) {
      setName("");
      setSlug("");
      createOrganization.reset();
    }
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    createOrganization.mutate(
      { name: name.trim(), slug: slug.trim() || undefined },
      {
        onSuccess: (organization) => {
          toast.success(`Organization "${organization.name}" created`);
          selectOrganization(organization.id);
          handleOpenChange(false);
          router.push(`/organizations/${organization.id}`);
        },
      },
    );
  }

  const generalError =
    createOrganization.isError && !fieldError("name") && !fieldError("slug")
      ? errorMessage(createOrganization.error)
      : undefined;

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger render={<Button />}>
        <PlusIcon />
        New organization
      </DialogTrigger>
      <DialogContent>
        <form onSubmit={handleSubmit} className="grid gap-4">
          <DialogHeader>
            <DialogTitle>Create organization</DialogTitle>
            <DialogDescription>
              Organizations group projects and members. You will be its Owner.
            </DialogDescription>
          </DialogHeader>
          <FormField id="org-name" label="Name" error={fieldError("name")}>
            <Input
              id="org-name"
              value={name}
              onChange={(event) => setName(event.target.value)}
              maxLength={100}
              required
              autoFocus
            />
          </FormField>
          <FormField
            id="org-slug"
            label="Slug (optional)"
            hint="Lowercase letters, numbers, and hyphens. Derived from the name if empty."
            error={fieldError("slug")}
          >
            <Input
              id="org-slug"
              value={slug}
              onChange={(event) => setSlug(event.target.value)}
              pattern="[a-z0-9]+(-[a-z0-9]+)*"
              minLength={3}
              maxLength={50}
            />
          </FormField>
          {generalError && <p className="text-sm text-destructive">{generalError}</p>}
          <DialogFooter>
            <Button type="submit" disabled={createOrganization.isPending || !name.trim()}>
              {createOrganization.isPending ? "Creating…" : "Create"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
