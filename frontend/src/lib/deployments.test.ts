import { describe, expect, it } from "vitest";
import type { Deployment } from "@/lib/api/types";
import { formatDuration, pollWhileInProgress, shortSha } from "./deployments";

describe("deployment helpers", () => {
  it("formats durations", () => {
    expect(formatDuration("2026-09-25T10:00:00Z", "2026-09-25T10:00:42Z")).toBe("42s");
    expect(formatDuration("2026-09-25T10:00:00Z", "2026-09-25T10:03:05Z")).toBe("3m 5s");
    expect(formatDuration(null, null)).toBe("—");
  });

  it("polls only while a deployment is in progress", () => {
    const running = { status: "BUILDING" } as Deployment;
    const done = { status: "SUCCEEDED" } as Deployment;
    expect(pollWhileInProgress([done, running])).toBe(2000);
    expect(pollWhileInProgress([done])).toBe(false);
    expect(pollWhileInProgress(undefined)).toBe(false);
  });

  it("shortens commit hashes", () => {
    expect(shortSha("0123456789abcdef")).toBe("0123456");
    expect(shortSha(null)).toBe("—");
  });
});
