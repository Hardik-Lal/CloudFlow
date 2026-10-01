import { apiFetch } from "./client";
import type { User } from "./types";

export const usersApi = {
  me: () => apiFetch<User>("/api/v1/users/me"),
};
