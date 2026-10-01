"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { organizationsApi, type CreateOrganizationInput } from "@/lib/api/organizations";
import type { Permission, Role } from "@/lib/api/types";
import { queryKeys } from "@/lib/query-keys";

export function useOrganizations() {
  return useQuery({ queryKey: queryKeys.organizations.all, queryFn: organizationsApi.list });
}

export function useOrganization(organizationId: string) {
  return useQuery({
    queryKey: queryKeys.organizations.detail(organizationId),
    queryFn: () => organizationsApi.get(organizationId),
  });
}

/** Whether the caller's organization role grants a permission (UI hint; the API enforces it). */
export function useOrganizationPermission(organizationId: string, permission: Permission) {
  const { data } = useOrganization(organizationId);
  return data?.permissions.includes(permission) ?? false;
}

export function useCreateOrganization() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: CreateOrganizationInput) => organizationsApi.create(input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.organizations.all }),
  });
}

export function useRenameOrganization(organizationId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (name: string) => organizationsApi.update(organizationId, name),
    onSuccess: (organization) => {
      queryClient.setQueryData(queryKeys.organizations.detail(organizationId), organization);
      return queryClient.invalidateQueries({ queryKey: queryKeys.organizations.all, exact: true });
    },
  });
}

export function useDeleteOrganization(organizationId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => organizationsApi.remove(organizationId),
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: queryKeys.organizations.detail(organizationId) });
      return queryClient.invalidateQueries({ queryKey: queryKeys.organizations.all, exact: true });
    },
  });
}

export function useMembers(organizationId: string) {
  return useQuery({
    queryKey: queryKeys.organizations.members(organizationId),
    queryFn: () => organizationsApi.members(organizationId),
  });
}

/**
 * Membership changes can affect the caller's own role and permissions, so every organization query
 * (list, detail, members) is refreshed.
 */
function useInvalidateOrganizations() {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: queryKeys.organizations.all });
}

export function useAddMember(organizationId: string) {
  const invalidate = useInvalidateOrganizations();
  return useMutation({
    mutationFn: ({ username, role }: { username: string; role: Role }) =>
      organizationsApi.addMember(organizationId, username, role),
    onSuccess: invalidate,
  });
}

export function useChangeMemberRole(organizationId: string) {
  const invalidate = useInvalidateOrganizations();
  return useMutation({
    mutationFn: ({ userId, role }: { userId: string; role: Role }) =>
      organizationsApi.changeMemberRole(organizationId, userId, role),
    onSuccess: invalidate,
  });
}

export function useRemoveMember(organizationId: string) {
  const invalidate = useInvalidateOrganizations();
  return useMutation({
    mutationFn: (userId: string) => organizationsApi.removeMember(organizationId, userId),
    onSuccess: invalidate,
  });
}

export function useAuditLogs(organizationId: string, page: number, action: string) {
  return useQuery({
    queryKey: queryKeys.organizations.audit(organizationId, page, action),
    queryFn: () => organizationsApi.auditLogs(organizationId, page, action || undefined),
    placeholderData: (previous) => previous,
  });
}
