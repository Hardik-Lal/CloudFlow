import { expect, test } from "@playwright/test";
import { ensureUser, signIn, uniqueName } from "./support";

test("owners manage an organization, members, and see the audit trail", async ({
  page,
  context,
}) => {
  const owner = ensureUser("e2e-owner", 900_000_001);
  const teammate = ensureUser("e2e-teammate", 900_000_002);
  await signIn(context, owner);

  await page.goto("/auth/callback");
  await expect(page).toHaveURL(/\/organizations$/);

  const name = uniqueName("E2E Org");
  await page.getByRole("button", { name: "New organization" }).click();
  await page.getByLabel("Name").fill(name);
  await page.getByRole("button", { name: "Create" }).click();
  await expect(page.getByRole("heading", { name })).toBeVisible();

  await page.getByRole("tab", { name: "Members" }).click();
  await page.getByRole("button", { name: "Add member" }).click();
  await page.getByLabel("GitHub username").fill(teammate.username);
  await page.getByRole("dialog").getByRole("button", { name: "Add member" }).click();
  await expect(page.getByText(`${teammate.username} added`)).toBeVisible();

  const row = page.getByRole("row", { name: new RegExp(teammate.username) });
  await row.getByRole("combobox").click();
  await page.getByRole("option", { name: /Viewer/ }).click();
  await expect(page.getByText(`${teammate.username} is now viewer`)).toBeVisible();

  await page.getByRole("tab", { name: "Audit log" }).click();
  await expect(page.getByRole("cell", { name: "member role changed" })).toBeVisible();
  await expect(page.getByRole("cell", { name: "member added" })).toBeVisible();
  await expect(page.getByRole("cell", { name: "organization created" })).toBeVisible();

  await page.getByRole("tab", { name: "Settings" }).click();
  await page.getByRole("button", { name: "Delete organization" }).click();
  await page.getByRole("alertdialog").getByRole("button", { name: "Delete" }).click();
  await expect(page).toHaveURL(/\/organizations$/);
  await expect(page.getByText(name)).toHaveCount(0);
});

test("viewers cannot manage members", async ({ page, context, browser }) => {
  const owner = ensureUser("e2e-owner", 900_000_001);
  const viewer = ensureUser("e2e-viewer", 900_000_003);
  await signIn(context, owner);
  await page.goto("/auth/callback");
  const name = uniqueName("E2E Viewer Org");
  await page.getByRole("button", { name: "New organization" }).click();
  await page.getByLabel("Name").fill(name);
  await page.getByRole("button", { name: "Create" }).click();
  await page.getByRole("tab", { name: "Members" }).click();
  await page.getByRole("button", { name: "Add member" }).click();
  await page.getByLabel("GitHub username").fill(viewer.username);
  await page.getByRole("dialog").getByRole("combobox").click();
  await page.getByRole("option", { name: /Viewer/ }).click();
  await page.getByRole("dialog").getByRole("button", { name: "Add member" }).click();
  await expect(page.getByText(`${viewer.username} added`)).toBeVisible();

  const viewerContext = await browser.newContext();
  await signIn(viewerContext, viewer);
  const viewerPage = await viewerContext.newPage();
  await viewerPage.goto("/auth/callback");
  await viewerPage.getByRole("link", { name: new RegExp(name) }).click();
  await viewerPage.getByRole("tab", { name: "Members" }).click();
  await expect(viewerPage.getByRole("button", { name: "Add member" })).toHaveCount(0);
  await expect(viewerPage.getByRole("tab", { name: "Audit log" })).toHaveCount(0);
  await viewerContext.close();
});
