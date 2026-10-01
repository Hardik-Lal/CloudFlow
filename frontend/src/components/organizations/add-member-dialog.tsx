"use client";

import { UserPlusIcon } from "lucide-react";
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
import { useAddMember } from "@/hooks/use-organizations";
import { errorMessage } from "@/lib/api/errors";
import type { Role } from "@/lib/api/types";
import { RoleSelect } from "./role-select";

interface AddMemberDialogProps {
  organizationId: string;
  assignableRoles: Role[];
}

export function AddMemberDialog({ organizationId, assignableRoles }: AddMemberDialogProps) {
  const [open, setOpen] = useState(false);
  const [username, setUsername] = useState("");
  const [role, setRole] = useState<Role>("DEVELOPER");
  const addMember = useAddMember(organizationId);

  function handleOpenChange(next: boolean) {
    setOpen(next);
    if (!next) {
      setUsername("");
      setRole("DEVELOPER");
      addMember.reset();
    }
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    addMember.mutate(
      { username: username.trim(), role },
      {
        onSuccess: (member) => {
          toast.success(`${member.username} added`);
          handleOpenChange(false);
        },
      },
    );
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger render={<Button />}>
        <UserPlusIcon />
        Add member
      </DialogTrigger>
      <DialogContent>
        <form onSubmit={handleSubmit} className="grid gap-4">
          <DialogHeader>
            <DialogTitle>Add member</DialogTitle>
            <DialogDescription>
              Enter the GitHub username of someone who has signed in to CloudFlow at least once.
            </DialogDescription>
          </DialogHeader>
          <FormField id="member-username" label="GitHub username">
            <Input
              id="member-username"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              autoComplete="off"
              required
              autoFocus
            />
          </FormField>
          <FormField id="member-role" label="Role">
            <RoleSelect id="member-role" value={role} roles={assignableRoles} onChange={setRole} />
          </FormField>
          {addMember.isError && (
            <p className="text-sm text-destructive">{errorMessage(addMember.error)}</p>
          )}
          <DialogFooter>
            <Button type="submit" disabled={addMember.isPending || !username.trim()}>
              {addMember.isPending ? "Adding…" : "Add member"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
