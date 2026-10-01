import { apiFetch } from "./client";
import type { GithubRepositoryPage } from "./types";

export const githubApi = {
  repositories: (page: number, perPage = 50) =>
    apiFetch<GithubRepositoryPage>(`/api/v1/github/repositories?page=${page}&perPage=${perPage}`),
};
