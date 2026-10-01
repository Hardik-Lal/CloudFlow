import { beforeEach, describe, expect, it } from "vitest";
import { consumeReturnTo, rememberReturnTo } from "./return-to";

describe("return-to", () => {
  beforeEach(() => sessionStorage.clear());

  it("returns the remembered relative path once", () => {
    rememberReturnTo("/organizations/abc");

    expect(consumeReturnTo()).toBe("/organizations/abc");
    expect(consumeReturnTo()).toBe("/organizations");
  });

  it.each(["https://evil.example", "//evil.example", "/\\evil.example", "relative", null])(
    "rejects unsafe value %s",
    (value) => {
      rememberReturnTo(value);
      expect(consumeReturnTo()).toBe("/organizations");
    },
  );
});
