import { expect, it } from "vitest";
import { theme } from "./theme";

it("lets a theme restyle surfaces, text and radii while keeping Colores' own look by default", () => {
  expect(theme.card.background).toBe("var(--colores-theme-surface-raised, #0c0c10)");
  expect(theme.color.textMuted).toBe("var(--colores-theme-text-muted, rgba(255,255,255,0.45))");
  expect(theme.radius.md).toBe("var(--colores-theme-radius-md, 14px)");
});
