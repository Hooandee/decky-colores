import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it, vi } from "vitest";

vi.mock("../i18n", () => ({
  useI18n: () => ({ t: (key: string) => key }),
}));

import { DevicePreview } from "./DevicePreview";

describe("DevicePreview capability layouts", () => {
  it("renders unknown physical topology as neutral lights", () => {
    const html = renderToStaticMarkup(createElement(DevicePreview, {
      colors: [{ r: 10, g: 20, b: 30 }],
      brightness: 80,
      power: true,
      layoutKind: "uniform",
      segments: 1,
    }));

    expect(html).toContain("layout.lights");
    expect(html).not.toContain("device.preview.rings");
  });

  it("keeps powered rings visible at low LED brightness", () => {
    const html = renderToStaticMarkup(createElement(DevicePreview, {
      colors: [
        { r: 0, g: 196, b: 255 },
        { r: 124, g: 92, b: 255 },
      ],
      brightness: 10,
      power: true,
      layoutKind: "rings",
    }));

    expect(html).toContain("rgb(14, 62, 77)");
    expect(html).toContain("rgb(44, 36, 77)");
  });
});
