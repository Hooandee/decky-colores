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
});
