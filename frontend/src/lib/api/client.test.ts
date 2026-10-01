import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { useAuthStore } from "@/stores/auth-store";
import { apiDownload, apiFetch, refreshSession } from "./client";
import { ApiError } from "./errors";

const session = {
  accessToken: "new-token",
  tokenType: "Bearer",
  expiresIn: 900,
  user: { id: "u1", username: "octocat", displayName: null, email: null, avatarUrl: null },
};

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

describe("api client", () => {
  const fetchMock = vi.fn<typeof fetch>();

  beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
    useAuthStore.setState({ accessToken: null, user: null, status: "unknown" });
  });

  afterEach(() => {
    fetchMock.mockReset();
    vi.unstubAllGlobals();
  });

  it("downloads files as blobs with the bearer token", async () => {
    useAuthStore.setState({ accessToken: "token", status: "authenticated" });
    fetchMock.mockResolvedValue(new Response("FROM python:3.12\n", { status: 200 }));

    const blob = await apiDownload("/api/v1/artifacts/a1/content");

    expect(await blob.text()).toBe("FROM python:3.12\n");
    const [, init] = fetchMock.mock.calls[0];
    expect(init?.headers).toMatchObject({ Authorization: "Bearer token", Accept: "*/*" });
  });

  it("shares one refresh request between concurrent callers", async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, session));

    const [first, second] = await Promise.all([refreshSession(), refreshSession()]);

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(first).toBe(second);
    expect(useAuthStore.getState()).toMatchObject({
      accessToken: "new-token",
      status: "authenticated",
    });
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toMatch(/\/api\/v1\/auth\/refresh$/);
    expect(init?.credentials).toBe("include");
  });

  it("retries a rejected refresh once because another tab may have rotated the cookie", async () => {
    fetchMock
      .mockResolvedValueOnce(jsonResponse(401, { status: 401 }))
      .mockResolvedValueOnce(jsonResponse(200, session));

    await expect(refreshSession()).resolves.toEqual(session);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("clears the session when refresh fails", async () => {
    useAuthStore.setState({ accessToken: "old", status: "authenticated" });
    fetchMock.mockResolvedValue(jsonResponse(401, { status: 401, title: "Unauthorized" }));

    await expect(refreshSession()).rejects.toBeInstanceOf(ApiError);
    expect(useAuthStore.getState().status).toBe("anonymous");
    expect(useAuthStore.getState().accessToken).toBeNull();
  });

  it("sends the bearer token and never the cookie to regular endpoints", async () => {
    useAuthStore.setState({ accessToken: "token-1", status: "authenticated" });
    fetchMock.mockResolvedValue(jsonResponse(200, []));

    await apiFetch("/api/v1/organizations");

    const init = fetchMock.mock.calls[0][1];
    expect(init?.credentials).toBe("omit");
    expect((init?.headers as Record<string, string>).Authorization).toBe("Bearer token-1");
  });

  it("refreshes and retries once when the access token has expired", async () => {
    useAuthStore.setState({ accessToken: "expired", status: "authenticated" });
    fetchMock
      .mockResolvedValueOnce(jsonResponse(401, { status: 401 }))
      .mockResolvedValueOnce(jsonResponse(200, session))
      .mockResolvedValueOnce(jsonResponse(200, [{ id: "org" }]));

    await expect(apiFetch("/api/v1/organizations")).resolves.toEqual([{ id: "org" }]);
    const retry = fetchMock.mock.calls[2][1];
    expect((retry?.headers as Record<string, string>).Authorization).toBe("Bearer new-token");
  });

  it("surfaces problem details for non-auth errors without refreshing", async () => {
    useAuthStore.setState({ accessToken: "token", status: "authenticated" });
    fetchMock.mockResolvedValue(
      jsonResponse(400, {
        status: 400,
        detail: "Request contains invalid fields",
        errors: [{ field: "name", message: "must not be blank" }],
      }),
    );

    const error = await apiFetch("/api/v1/organizations").catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).fieldErrors).toEqual([
      { field: "name", message: "must not be blank" },
    ]);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
