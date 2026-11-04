import { RGB } from "./types";

export type StyleMode =
  | "color"
  | "gradient"
  | "effect"
  | "ambilight"
  | "audio"
  | "battery"
  | "temperature"
  | "performance"
  | "clock";

export interface SavedStyle {
  name: string;
  mode?: StyleMode;
  colors?: RGB[];
  gradient?: RGB[];
}

export interface StylesImport {
  styles: SavedStyle[];
  imported: string[];
  added: number;
  replaced: number;
  skipped: number;
}

const DECKY_MODES: Partial<Record<StyleMode, string>> = { color: "solid", audio: "vu", ambilight: "ambient" };

export const deckyModeOf = (style: SavedStyle): string | null =>
  style.mode ? (DECKY_MODES[style.mode] ?? style.mode) : null;

export const stylesForMode = (styles: SavedStyle[], mode: string): SavedStyle[] =>
  styles.filter((style) => deckyModeOf(style) === mode);

export const STYLES_FOLDER_FALLBACK = "/home/deck/Documents/Colores";
export const STYLE_NAME_MAX = 40;

export const styleSwatch = (style: SavedStyle): string => {
  const stops = style.mode === "gradient" && style.gradient?.length ? style.gradient : style.colors;
  if (!stops?.length) return "rgba(255,255,255,0.18)";
  if (stops.length === 1) return `rgb(${stops[0].r}, ${stops[0].g}, ${stops[0].b})`;
  return `linear-gradient(135deg, ${stops.map((c) => `rgb(${c.r}, ${c.g}, ${c.b})`).join(", ")})`;
};
