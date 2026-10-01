import { describe, expect, it } from "vitest";
import { niceCeiling } from "./time-series-chart";

describe("niceCeiling", () => {
  it.each([
    [0, 1],
    [0.7, 1],
    [1.3, 2],
    [3.2, 5],
    [42, 50],
    [180, 200],
    [512, 1000],
  ])("rounds %d up to %d", (value, expected) => {
    expect(niceCeiling(value)).toBe(expected);
  });
});
