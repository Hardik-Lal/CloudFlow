import { describe, expect, it } from "vitest";
import { runOutcome } from "./pipelines";

describe("runOutcome", () => {
  it.each([
    [{ status: "in_progress", conclusion: null }, "running"],
    [{ status: "queued", conclusion: null }, "running"],
    [{ status: "completed", conclusion: "success" }, "success"],
    [{ status: "completed", conclusion: "failure" }, "failure"],
    [{ status: "completed", conclusion: "timed_out" }, "failure"],
    [{ status: "completed", conclusion: "cancelled" }, "cancelled"],
    [{ status: "completed", conclusion: "skipped" }, "skipped"],
  ])("maps %j to %s", (run, expected) => {
    expect(runOutcome(run)).toBe(expected);
  });
});
