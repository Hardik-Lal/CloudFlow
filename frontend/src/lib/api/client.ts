import { env } from "@/lib/env";
import { useAuthStore } from "@/stores/auth-store";
import { ApiError, type ProblemDetail } from "./errors";
import type { AuthTokenResponse } from "./types";

type HttpMethod = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";

interface RequestOptions {
  method?: HttpMethod;
  body?: unknown;
  signal?: AbortSignal;
  /** "blob" returns the raw response body (file downloads). */
  responseType?: "json" | "blob";
}

const AUTH_PATH = "/api/v1/auth";

let refreshInFlight: Promise<AuthTokenResponse> | null = null;

/**
 * Exchanges the HttpOnly refresh cookie for a new access token.
 *
 * Concurrent callers share one request: refresh tokens rotate on every use, so parallel refreshes
 * with the same cookie would be treated as token reuse by the backend.
 */
export function refreshSession(): Promise<AuthTokenResponse> {
  refreshInFlight ??= (async () => {
    try {
      const session = await requestRefresh();
      useAuthStore.getState().setSession(session);
      return session;
    } catch (error) {
      useAuthStore.getState().clearSession();
      throw error;
    } finally {
      refreshInFlight = null;
    }
  })();
  return refreshInFlight;
}

/**
 * Another browser tab may have rotated the shared cookie a moment earlier, which makes our request
 * carry a just-revoked token. By the time the 401 arrives the browser holds the new cookie, so a
 * single retry recovers.
 */
async function requestRefresh(): Promise<AuthTokenResponse> {
  const refresh = () => request<AuthTokenResponse>(`${AUTH_PATH}/refresh`, { method: "POST" });
  try {
    return await refresh();
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return refresh();
    }
    throw error;
  }
}

export async function logout(): Promise<void> {
  try {
    await request<void>(`${AUTH_PATH}/logout`, { method: "POST" });
  } finally {
    useAuthStore.getState().clearSession();
  }
}

export function githubLoginUrl(): string {
  return `${env.apiBaseUrl}/oauth2/authorization/github`;
}

/**
 * Calls an authenticated API endpoint. On 401 the session is refreshed once and the request is
 * retried; if the refresh fails the session is cleared and the error propagates.
 */
export async function apiFetch<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const token = useAuthStore.getState().accessToken;
  try {
    return await request<T>(path, options, token);
  } catch (error) {
    if (!(error instanceof ApiError) || error.status !== 401) {
      throw error;
    }
    const session = await refreshSession();
    return request<T>(path, options, session.accessToken);
  }
}

/** Downloads a file from an authenticated endpoint. */
export function apiDownload(path: string): Promise<Blob> {
  return apiFetch<Blob>(path, { responseType: "blob" });
}

async function request<T>(
  path: string,
  { method = "GET", body, signal, responseType = "json" }: RequestOptions,
  accessToken?: string | null,
): Promise<T> {
  const headers: Record<string, string> = {
    Accept: responseType === "blob" ? "*/*" : "application/json",
  };
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  if (accessToken) {
    headers.Authorization = `Bearer ${accessToken}`;
  }

  const response = await fetch(`${env.apiBaseUrl}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    // Only the auth endpoints need the refresh cookie; everything else uses the bearer token.
    credentials: path.startsWith(AUTH_PATH) ? "include" : "omit",
    signal,
  });

  if (!response.ok) {
    throw new ApiError(response.status, await readProblem(response));
  }
  if (response.status === 204) {
    return undefined as T;
  }
  if (responseType === "blob") {
    return (await response.blob()) as T;
  }
  return (await response.json()) as T;
}

async function readProblem(response: Response): Promise<ProblemDetail> {
  try {
    return (await response.json()) as ProblemDetail;
  } catch {
    return { status: response.status, title: response.statusText };
  }
}
