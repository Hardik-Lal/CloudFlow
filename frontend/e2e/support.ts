import { execFileSync } from "node:child_process";
import { createHash, randomBytes } from "node:crypto";
import type { BrowserContext } from "@playwright/test";

export const API_URL = process.env.E2E_API_URL ?? "http://localhost:8080";
/** The stack's PostgreSQL container (Compose project "cloudflow"). */
const POSTGRES_CONTAINER = process.env.E2E_POSTGRES_CONTAINER ?? "cloudflow-postgres-1";

function psql(sql: string): string {
  return execFileSync(
    "docker",
    ["exec", POSTGRES_CONTAINER, "psql", "-U", "cloudflow", "-d", "cloudflow", "-tAq", "-c", sql],
    { encoding: "utf8" },
  ).trim();
}

export interface TestUser {
  id: string;
  username: string;
}

/**
 * Creates (or reuses) a user the way a GitHub sign-in would. Test-only: talks to the test stack's
 * database directly.
 */
export function ensureUser(username: string, githubId: number): TestUser {
  const id = psql(
    `INSERT INTO users (github_id, username, display_name) VALUES (${githubId}, '${username}', '${username}')
     ON CONFLICT (github_id) DO UPDATE SET username = EXCLUDED.username RETURNING id`,
  );
  return { id, username };
}

/**
 * Signs the browser in: issues a refresh token for the user (exactly what CloudFlow does at the end
 * of the GitHub OAuth flow) and stores it in the HttpOnly cookie the backend expects.
 */
export async function signIn(context: BrowserContext, user: TestUser): Promise<void> {
  const token = randomBytes(32).toString("base64url");
  const hash = createHash("sha256").update(token).digest("hex");
  psql(
    `INSERT INTO refresh_tokens (user_id, token_hash, expires_at) VALUES ('${user.id}', '${hash}', now() + interval '1 hour')`,
  );
  const api = new URL(API_URL);
  await context.addCookies([
    {
      name: "cloudflow_refresh",
      value: token,
      domain: api.hostname,
      path: "/api/v1/auth",
      httpOnly: true,
      sameSite: "Strict",
    },
  ]);
}

/** GitHub ids of E2E users start here; real GitHub ids never collide with them in a test stack. */
export const E2E_GITHUB_ID_BASE = 900_000_000;

/**
 * Removes everything the suite created: organizations E2E users belong to (their projects,
 * environments, and audit entries cascade), then the users and their account-level audit entries.
 */
export function removeTestData(): void {
  psql(
    `DELETE FROM organizations WHERE id IN (
       SELECT m.organization_id FROM organization_memberships m JOIN users u ON u.id = m.user_id
       WHERE u.github_id >= ${E2E_GITHUB_ID_BASE} AND u.username LIKE 'e2e-%');
     DELETE FROM audit_logs WHERE organization_id IS NULL AND actor_username LIKE 'e2e-%';
     DELETE FROM users WHERE github_id >= ${E2E_GITHUB_ID_BASE} AND username LIKE 'e2e-%'`,
  );
}

export function uniqueName(prefix: string): string {
  return `${prefix} ${Date.now().toString(36)}`;
}
