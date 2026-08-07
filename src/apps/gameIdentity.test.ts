import { describe, expect, it } from "vitest";

import { stableGameKey } from "./gameIdentity";

describe("stableGameKey", () => {
  it("keeps the numeric appid for Steam games", () => {
    expect(stableGameKey({ appid: 570, display_name: "Dota 2" })).toBe("570");
  });

  it("keeps a non-Steam shortcut stable when its live appid changes", () => {
    const first = stableGameKey({
      appid: 3400000000,
      display_name: "Moonlight",
      app_type: 1073741824,
    });
    const second = stableGameKey({
      appid: 2900000001,
      display_name: "Moonlight",
      app_type: 1073741824,
    });

    expect(first).toBe("ns:moonlight");
    expect(second).toBe(first);
  });
});
