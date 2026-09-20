import { FALLBACK_ACCENT_RGB } from "./accent";

const accentRgb = `var(--colores-accent-rgb, ${FALLBACK_ACCENT_RGB})`;

const color = {
  surfaceRaised: "#0c0c10",
  hairline: "rgba(255,255,255,0.06)",
  textPrimary: "rgba(255,255,255,0.92)",
  textMuted: "rgba(255,255,255,0.45)",
  accent: `rgb(${accentRgb})`,
  accentRgb,
  onAccent: "#06121f",
  warn: "#ffb454",
  ok: "#7ee0a0",
  danger: "#e05a5a",
} as const;

const radius = { sm: 8, md: 14 } as const;
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
