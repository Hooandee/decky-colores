import { FALLBACK_ACCENT_RGB } from "./accent";

const accentRgb = `var(--colores-accent-rgb, ${FALLBACK_ACCENT_RGB})`;

// A CSS Loader theme can restyle these by publishing --colores-theme-* on :root.
const themed = (name: string, fallback: string) => `var(--colores-theme-${name}, ${fallback})`;

const color = {
  surfaceRaised: themed("surface-raised", "#0c0c10"),
  hairline: themed("hairline", "rgba(255,255,255,0.06)"),
  textPrimary: themed("text", "rgba(255,255,255,0.92)"),
  textMuted: themed("text-muted", "rgba(255,255,255,0.45)"),
  accent: `rgb(${accentRgb})`,
  accentRgb,
  onAccent: themed("on-accent", "#06121f"),
  warn: "#ffb454",
  ok: "#7ee0a0",
  danger: "#e05a5a",
} as const;

const radius = { sm: themed("radius-sm", "8px"), md: themed("radius-md", "14px") } as const;
const space = { xs: 4, sm: 8, md: 12, lg: 18, section: 14 } as const;
const font = { caption: 11, body: 13, value: 28 } as const;

export const theme = {
  color,
  radius,
  space,
  font,
  card: {
    borderRadius: radius.md,
    background: color.surfaceRaised,
    boxShadow: `inset 0 0 0 1px ${color.hairline}`,
  },
  sectionLabel: {
    fontSize: font.caption,
    color: color.textMuted,
    textTransform: "uppercase",
    letterSpacing: 0.5,
  },
} as const;
