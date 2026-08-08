import { afterEach, describe, expect, it, vi } from "vitest";
import { readFileSync } from "node:fs";
import {
  audioVuColors,
  formatSensorValue,
  sensorBandColor,
  sensorScalePosition,
  sensorThresholdBounds,
  sensorThresholdPositions,
  suggestGradientName,
} from "./palette";

type VuVector = {
  id: string;
  operation: string;
  input: { level: number; zones: number };
  expected: { colors: Array<{ r: number; g: number; b: number }> };
};

const vectors = JSON.parse(
  readFileSync(new URL("../shared/golden/vu.json", import.meta.url), "utf8"),
) as VuVector[];

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

  it("matches every shared VU golden vector", () => {
    for (const vector of vectors) {
      expect(vector.operation).toBe("vu_frame");
      expect(audioVuColors(vector.input.level, vector.input.zones), vector.id).toEqual(vector.expected.colors);
    }
  });
});

describe("custom sensor bands", () => {
  const bands = [
    { min: 90, color: { r: 1, g: 2, b: 3 } },
    { min: 70, color: { r: 4, g: 5, b: 6 } },
    { min: 50, color: { r: 7, g: 8, b: 9 } },
    { min: 25, color: { r: 10, g: 11, b: 12 } },
    { min: 0, color: { r: 13, g: 14, b: 15 } },
  ];

  it("selects the color for any sensor scale", () => {
    expect(sensorBandColor(75, bands)).toEqual({ r: 4, g: 5, b: 6 });
    expect(sensorBandColor(52, bands)).toEqual({ r: 7, g: 8, b: 9 });
  });

  it("keeps editable thresholds between their neighbours", () => {
    expect(sensorThresholdBounds("battery", 0, bands)).toEqual({ min: 71, max: 100 });
    expect(sensorThresholdBounds("battery", 2, bands)).toEqual({ min: 26, max: 69 });
    expect(sensorThresholdBounds("temperature", 0, bands)).toEqual({ min: 71, max: 120 });
    expect(sensorThresholdBounds("battery", 4, bands)).toBeNull();
  });

  it("positions threshold ticks on the displayed scale", () => {
    expect(sensorThresholdPositions("battery", bands)).toEqual([90, 70, 50, 25]);
    expect(sensorThresholdPositions("temperature", bands)).toEqual([
      92.85714285714286,
      64.28571428571429,
      35.714285714285715,
      0,
    ]);
    expect(sensorScalePosition("temperature", bands, 52)).toBeCloseTo(38.57);
  });

  it("keeps fractional temperature readings below their threshold", () => {
    expect(sensorBandColor(89.6, bands)).toEqual({ r: 4, g: 5, b: 6 });
    expect(formatSensorValue(89.6, "es")).toBe("89,6");
    expect(formatSensorValue(89.6, "en")).toBe("89.6");
    expect(formatSensorValue(89.6, "it")).toBe("89,6");
  });

  it("formats battery percentages without floating point artifacts", () => {
    expect(formatSensorValue(14.000000000000002, "es", 0)).toBe("14");
  });
});

describe("Italian gradient names", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("combines an Italian noun and adjective in natural order", () => {
    vi.spyOn(Math, "random").mockReturnValue(0);

    expect(suggestGradientName("it")).toBe("Tramonto elettrico");
  });
});
