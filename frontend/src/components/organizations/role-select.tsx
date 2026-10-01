"use client";

import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { Role } from "@/lib/api/types";
import { ROLE_DESCRIPTIONS, ROLE_LABELS } from "@/lib/roles";

interface RoleSelectProps {
  value: Role;
  roles: Role[];
  onChange: (role: Role) => void;
  disabled?: boolean;
  id?: string;
}

export function RoleSelect({ value, roles, onChange, disabled, id }: RoleSelectProps) {
  return (
    <Select
      items={ROLE_LABELS}
      value={value}
      onValueChange={(role) => role && onChange(role as Role)}
      disabled={disabled}
    >
      <SelectTrigger id={id} className="w-36" aria-label="Role">
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        {roles.map((role) => (
          <SelectItem key={role} value={role}>
            <div className="grid">
              <span>{ROLE_LABELS[role]}</span>
              <span className="text-xs text-muted-foreground">{ROLE_DESCRIPTIONS[role]}</span>
            </div>
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}
