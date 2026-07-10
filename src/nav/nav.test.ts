import { describe, it, expect } from "vitest";
import { cycleTab } from "./nav";

describe("cycleTab", () => {
  const ids = ["a", "b", "c"];

  it("moves next and previous", () => {
    expect(cycleTab(ids, "a", 1)).toBe("b");
    expect(cycleTab(ids, "b", -1)).toBe("a");
  });

  it("wraps around both ends", () => {
    expect(cycleTab(ids, "c", 1)).toBe("a");
    expect(cycleTab(ids, "a", -1)).toBe("c");
  });

  it("returns the active id unchanged when it isn't in the list", () => {
    expect(cycleTab(ids, "z", 1)).toBe("z");
  });

  it("returns the active id unchanged for an empty list", () => {
    expect(cycleTab([], "a", 1)).toBe("a");
  });
});
