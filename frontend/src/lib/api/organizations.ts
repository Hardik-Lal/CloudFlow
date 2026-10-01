import { apiFetch } from "./client";
import type { AuditLogEntry, Member, Organization, Page, Role } from "./types";

export interface CreateOrganizationInput {
  name: string;
  slug?: string;
}

export const organizationsApi = {
  list: () => apiFetch<Organization[]>("/api/v1/organizations"),
  get: (organizationId: string) =>
    apiFetch<Organization>(`/api/v1/organizations/${organizationId}`),
  create: (input: CreateOrganizationInput) =>
    apiFetch<Organization>("/api/v1/organizations", { method: "POST", body: input }),
  update: (organizationId: string, name: string) =>
    apiFetch<Organization>(`/api/v1/organizations/${organizationId}`, {
      method: "PATCH",
      body: { name },
    }),
  remove: (organizationId: string) =>
    apiFetch<void>(`/api/v1/organizations/${organizationId}`, { method: "DELETE" }),

  members: (organizationId: string) =>
    apiFetch<Member[]>(`/api/v1/organizations/${organizationId}/members`),
  addMember: (organizationId: string, username: string, role: Role) =>
    apiFetch<Member>(`/api/v1/organizations/${organizationId}/members`, {
      method: "POST",
      body: { username, role },
    }),
  changeMemberRole: (organizationId: string, userId: string, role: Role) =>
    apiFetch<Member>(`/api/v1/organizations/${organizationId}/members/${userId}`, {
      method: "PATCH",
      body: { role },
    }),
  auditLogs: (organizationId: string, page = 0, action?: string) =>
    apiFetch<Page<AuditLogEntry>>(
      `/api/v1/organizations/${organizationId}/audit-logs?page=${page}&size=50${action ? `&action=${action}` : ""}`,
    ),
  removeMember: (organizationId: string, userId: string) =>
    apiFetch<void>(`/api/v1/organizations/${organizationId}/members/${userId}`, {
      method: "DELETE",
    }),
};
