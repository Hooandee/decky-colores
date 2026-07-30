import { describe, expect, it } from "vitest";

import { unifyColors } from "./color";

describe("unifyColors", () => {
  it("averages independent samples into one global color", () => {
    expect(
      unifyColors([
        { r: 255, g: 0, b: 0 },
        { r: 0, g: 0, b: 255 },
      ]),
    ).toEqual([{ r: 128, g: 0, b: 128 }]);
  });

  it("uses a safe black sample when no colors are available", () => {
    expect(unifyColors([])).toEqual([{ r: 0, g: 0, b: 0 }]);
  });
});
