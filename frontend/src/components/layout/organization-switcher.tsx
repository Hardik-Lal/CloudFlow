"use client";

import { useParams, useRouter } from "next/navigation";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { useOrganizations } from "@/hooks/use-organizations";
import { useOrganizationStore } from "@/stores/organization-store";

export function OrganizationSwitcher() {
  const { data: organizations } = useOrganizations();
  const params = useParams<{ organizationId?: string }>();
  const selectedId = useOrganizationStore((state) => state.selectedOrganizationId);
  const selectOrganization = useOrganizationStore((state) => state.selectOrganization);
  const router = useRouter();

  if (!organizations || organizations.length === 0) {
    return null;
  }

  const current = params.organizationId ?? selectedId;
  const items = Object.fromEntries(organizations.map((org) => [org.id, org.name]));

  return (
    <Select
      items={items}
      value={current && items[current] ? current : null}
      onValueChange={(organizationId) => {
        if (organizationId) {
          selectOrganization(organizationId);
          router.push(`/organizations/${organizationId}`);
        }
      }}
    >
      <SelectTrigger className="w-52" aria-label="Switch organization">
        <SelectValue placeholder="Select organization" />
      </SelectTrigger>
      <SelectContent>
        {organizations.map((organization) => (
          <SelectItem key={organization.id} value={organization.id}>
            {organization.name}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}
