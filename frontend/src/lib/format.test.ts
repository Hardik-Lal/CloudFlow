import { describe, expect, it } from "vitest";
import { formatRelativeTime } from "./format";

describe("formatRelativeTime", () => {
  const now = Date.parse("2026-09-25T12:00:00Z");

  it.each([
    ["2026-09-25T11:59:30Z", "30 seconds ago"],
    ["2026-09-25T09:00:00Z", "3 hours ago"],
    ["2026-09-24T12:00:00Z", "yesterday"],
    ["2026-08-01T12:00:00Z", "2 months ago"],
  ])("formats %s as %s", (iso, expected) => {
    expect(formatRelativeTime(iso, now)).toBe(expected);
  });

  it("renders a dash for missing values", () => {
    expect(formatRelativeTime(null, now)).toBe("—");
  });
});
