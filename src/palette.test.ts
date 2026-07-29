import { describe, expect, it } from "vitest";
import { audioVuColors } from "./palette";

describe("audioVuColors", () => {
  it("renders a two-zone center pair as visible green", () => {
    const colors = audioVuColors(0.6, 2);

    expect(colors[0]).toEqual(colors[1]);
    expect(colors[0].g).toBeGreaterThan(colors[0].r);
    expect(colors[0].r + colors[0].g + colors[0].b).toBeGreaterThan(100);
  });

  it("returns no colors when the device has no zones", () => {
    expect(audioVuColors(0.6, 0)).toEqual([]);
  });
});
