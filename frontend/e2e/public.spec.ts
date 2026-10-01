import { expect, test } from "@playwright/test";
import { API_URL } from "./support";

test("landing page links to GitHub sign-in on the API", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "CloudFlow" })).toBeVisible();
  const signIn = page.getByRole("link", { name: "Sign in with GitHub" });
  await expect(signIn).toHaveAttribute("href", `${API_URL}/oauth2/authorization/github`);
});

test("protected pages redirect to login with a return path", async ({ page }) => {
  await page.goto("/organizations");
  await expect(page).toHaveURL(/\/login\?returnTo=%2Forganizations/);
  await expect(page.getByText("Sign in to CloudFlow")).toBeVisible();
});

test("the frontend sends security headers", async ({ request }) => {
  const response = await request.get("/login");
  const headers = response.headers();
  expect(headers["content-security-policy"]).toContain("frame-ancestors 'none'");
  expect(headers["x-frame-options"]).toBe("DENY");
  expect(headers["x-content-type-options"]).toBe("nosniff");
});

test("the API rejects anonymous calls with problem details", async ({ request }) => {
  const response = await request.get(`${API_URL}/api/v1/organizations`);
  expect(response.status()).toBe(401);
  expect(response.headers()["content-type"]).toContain("application/problem+json");
  expect(await response.json()).toMatchObject({ type: "urn:cloudflow:problem:unauthorized" });
});

test("GitHub sign-in starts an OAuth authorization with PKCE", async ({ request }) => {
  const response = await request.get(`${API_URL}/oauth2/authorization/github`, { maxRedirects: 0 });
  expect(response.status()).toBe(302);
  const location = response.headers()["location"];
  expect(location).toMatch(/^https:\/\/github\.com\/login\/oauth\/authorize/);
  expect(location).toContain("code_challenge_method=S256");
  expect(location).toContain("scope=read:user%20user:email%20repo%20workflow");
});
