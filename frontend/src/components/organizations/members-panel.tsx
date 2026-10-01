"use client";

import { useRouter } from "next/navigation";
import { toast } from "sonner";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { RoleBadge } from "@/components/role-badge";
import { UserAvatar } from "@/components/user-avatar";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { useChangeMemberRole, useMembers, useRemoveMember } from "@/hooks/use-organizations";
import { errorMessage } from "@/lib/api/errors";
import type { Member, Organization } from "@/lib/api/types";
import { assignableRoles, canManageMember } from "@/lib/roles";
import { useAuthStore } from "@/stores/auth-store";
import { AddMemberDialog } from "./add-member-dialog";
import { RoleSelect } from "./role-select";

export function MembersPanel({ organization }: { organization: Organization }) {
  const { data: members, isPending, error } = useMembers(organization.id);
  const currentUserId = useAuthStore((state) => state.user?.id);
  const canManage = organization.permissions.includes("MEMBER_MANAGE");

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-4">
        <p className="text-sm text-muted-foreground">
          People with access to {organization.name} and their roles.
        </p>
        {canManage && (
          <AddMemberDialog
            organizationId={organization.id}
            assignableRoles={assignableRoles(organization.role)}
          />
        )}
      </div>

      {isPending && <Skeleton className="h-40" />}
      {error && (
        <Alert variant="destructive">
          <AlertDescription>{errorMessage(error)}</AlertDescription>
        </Alert>
      )}
      {members && (
        <div className="rounded-xl border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Member</TableHead>
                <TableHead>Role</TableHead>
                <TableHead>Joined</TableHead>
                <TableHead className="w-24" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {members.map((member) => (
                <MemberRow
                  key={member.userId}
                  member={member}
                  organization={organization}
                  isSelf={member.userId === currentUserId}
                  canManage={canManage && canManageMember(organization.role, member.role)}
                />
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </div>
  );
}

interface MemberRowProps {
  member: Member;
  organization: Organization;
  isSelf: boolean;
  canManage: boolean;
}

function MemberRow({ member, organization, isSelf, canManage }: MemberRowProps) {
  const changeRole = useChangeMemberRole(organization.id);
  const removeMember = useRemoveMember(organization.id);
  const router = useRouter();

  function handleRemove() {
    removeMember.mutate(member.userId, {
      onSuccess: () => {
        if (isSelf) {
          toast.success(`You left ${organization.name}`);
          router.replace("/organizations");
        } else {
          toast.success(`${member.username} removed`);
        }
      },
      onError: (error) => toast.error(errorMessage(error)),
    });
  }

  return (
    <TableRow>
      <TableCell>
        <div className="flex items-center gap-3">
          <UserAvatar username={member.username} avatarUrl={member.avatarUrl} className="size-8" />
          <div>
            <div className="font-medium">
              {member.displayName ?? member.username}
              {isSelf && <span className="ml-1 text-muted-foreground">(you)</span>}
            </div>
            <div className="text-xs text-muted-foreground">@{member.username}</div>
          </div>
        </div>
      </TableCell>
      <TableCell>
        {canManage ? (
          <RoleSelect
            value={member.role}
            roles={assignableRoles(organization.role)}
            disabled={changeRole.isPending}
            onChange={(role) =>
              changeRole.mutate(
                { userId: member.userId, role },
                {
                  onSuccess: () => toast.success(`${member.username} is now ${role.toLowerCase()}`),
                  onError: (error) => toast.error(errorMessage(error)),
                },
              )
            }
          />
        ) : (
          <RoleBadge role={member.role} />
        )}
      </TableCell>
      <TableCell className="text-muted-foreground">
        {new Date(member.joinedAt).toLocaleDateString()}
      </TableCell>
      <TableCell className="text-right">
        {(canManage || isSelf) && (
          <ConfirmDialog
            trigger={
              <Button variant="ghost" size="sm" disabled={removeMember.isPending}>
                {isSelf ? "Leave" : "Remove"}
              </Button>
            }
            title={isSelf ? `Leave ${organization.name}?` : `Remove ${member.username}?`}
            description={
              isSelf
                ? "You will lose access to this organization until an admin adds you again."
                : `${member.username} will lose access to all projects in ${organization.name}.`
            }
            confirmLabel={isSelf ? "Leave" : "Remove"}
            onConfirm={handleRemove}
          />
        )}
      </TableCell>
    </TableRow>
  );
}
