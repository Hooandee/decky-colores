import { describe, expect, it } from "vitest";

import { isProfileScopeVisible, PINNED_TAB } from "./manifest";

describe("profile scope visibility", () => {
  it("hides Global/Juego only on Ajustes", () => {
    expect(isProfileScopeVisible(PINNED_TAB)).toBe(false);
    expect(isProfileScopeVisible("solid")).toBe(true);
    expect(isProfileScopeVisible("sensors")).toBe(true);
  });
});
