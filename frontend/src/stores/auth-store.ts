import { create } from "zustand";
import type { AuthTokenResponse, User } from "@/lib/api/types";

type AuthStatus = "unknown" | "authenticated" | "anonymous";

interface AuthState {
  /** Kept in memory only; the refresh token lives in an HttpOnly cookie. */
  accessToken: string | null;
  user: User | null;
  status: AuthStatus;
  setSession: (session: AuthTokenResponse) => void;
  clearSession: () => void;
}

export const useAuthStore = create<AuthState>()((set) => ({
  accessToken: null,
  user: null,
  status: "unknown",
  setSession: (session) =>
    set({ accessToken: session.accessToken, user: session.user, status: "authenticated" }),
  clearSession: () => set({ accessToken: null, user: null, status: "anonymous" }),
}));
