import { describe, expect, it } from "vitest";

import { profileScopeFor } from "./scope";

describe("profileScopeFor", () => {
  it("shows Global without a running game or while that game follows Global", () => {
    expect(profileScopeFor(null, false)).toBe("global");
    expect(profileScopeFor("42", true)).toBe("global");
  });

  it("shows Juego only for a running game with its own active profile", () => {
    expect(profileScopeFor("42", false)).toBe("game");
  });
});
