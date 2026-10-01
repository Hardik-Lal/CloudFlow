import { describe, expect, it } from "vitest";
import { formatBytes, formatPercent, formatUptime } from "./units";

describe("units", () => {
  it("formats bytes", () => {
    expect(formatBytes(512)).toBe("512 B");
    expect(formatBytes(64 * 1024 * 1024)).toBe("64 MB");
    expect(formatBytes(1.5 * 1024 * 1024 * 1024)).toBe("1.5 GB");
  });

  it("formats uptime", () => {
    expect(formatUptime(42)).toBe("42s");
    expect(formatUptime(3 * 3600 + 5 * 60)).toBe("3h 5m");
    expect(formatUptime(2 * 86400 + 3600)).toBe("2d 1h");
  });

  it("formats percentages", () => {
    expect(formatPercent(99.456)).toBe("99.5%");
    expect(formatPercent(null)).toBe("—");
  });
});
