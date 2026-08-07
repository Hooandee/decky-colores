import { describe, expect, it } from "vitest";

import { nextProfileScope } from "./scope";

describe("nextProfileScope", () => {
  it("falls back to global when the running game disappears", () => {
    expect(nextProfileScope("game", null)).toBe("global");
  });

  it("keeps game selected when its profile follows global", () => {
    expect(nextProfileScope("game", { key: "42" })).toBe("game");
  });
});
