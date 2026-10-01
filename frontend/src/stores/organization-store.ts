import { create } from "zustand";
import { createJSONStorage, persist } from "zustand/middleware";

interface OrganizationState {
  selectedOrganizationId: string | null;
  selectOrganization: (organizationId: string | null) => void;
}

/** Remembers the organization the user last worked in (a UI preference, not a permission). */
export const useOrganizationStore = create<OrganizationState>()(
  persist(
    (set) => ({
      selectedOrganizationId: null,
      selectOrganization: (organizationId) => set({ selectedOrganizationId: organizationId }),
    }),
    {
      name: "cloudflow.organization",
      storage: createJSONStorage(() => localStorage),
    },
  ),
);
