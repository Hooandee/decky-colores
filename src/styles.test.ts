import { describe, expect, it } from "vitest";
import { stylesForMode, styleSwatch } from "./styles";

describe("styleSwatch", () => {
  it("uses the gradient for gradient styles and the color otherwise", () => {
    const gradient = [
      { r: 1, g: 2, b: 3 },
      { r: 4, g: 5, b: 6 },
    ];
    expect(styleSwatch({ name: "g", mode: "gradient", gradient, colors: [{ r: 9, g: 9, b: 9 }] })).toBe(
      "linear-gradient(135deg, rgb(1, 2, 3), rgb(4, 5, 6))",
    );
    expect(styleSwatch({ name: "c", mode: "color", gradient, colors: [{ r: 9, g: 9, b: 9 }] })).toBe("rgb(9, 9, 9)");
    expect(styleSwatch({ name: "empty" })).toBe("rgba(255,255,255,0.18)");
  });
});

describe("stylesForMode", () => {
  it("matches neutral style modes to the Decky mode", () => {
    const styles = [
      { name: "a", mode: "color" as const },
      { name: "b", mode: "effect" as const },
      { name: "c", mode: "color" as const },
      { name: "d" },
    ];
    expect(stylesForMode(styles, "solid").map((s) => s.name)).toEqual(["a", "c"]);
    expect(stylesForMode(styles, "effect").map((s) => s.name)).toEqual(["b"]);
    expect(stylesForMode(styles, "ambient")).toEqual([]);
  });
});
