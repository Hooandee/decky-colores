import { RGB } from "./types";

export interface HSV {
  h: number;
  s: number;
  v: number;
}

const clamp = (value: number, min: number, max: number) =>
  Math.min(max, Math.max(min, value));

export function hsvToRgb(h: number, s: number, v: number): RGB {
  const sat = clamp(s, 0, 100) / 100;
  const val = clamp(v, 0, 100) / 100;
  const hue = ((h % 360) + 360) % 360;

  const c = val * sat;
  const x = c * (1 - Math.abs(((hue / 60) % 2) - 1));
  const m = val - c;

  let r = 0;
  let g = 0;
  let b = 0;
  if (hue < 60) [r, g, b] = [c, x, 0];
  else if (hue < 120) [r, g, b] = [x, c, 0];
  else if (hue < 180) [r, g, b] = [0, c, x];
  else if (hue < 240) [r, g, b] = [0, x, c];
  else if (hue < 300) [r, g, b] = [x, 0, c];
  else [r, g, b] = [c, 0, x];

  return {
    r: Math.round((r + m) * 255),
    g: Math.round((g + m) * 255),
    b: Math.round((b + m) * 255),
  };
}

export function rgbToHsv({ r, g, b }: RGB): HSV {
  const rn = clamp(r, 0, 255) / 255;
  const gn = clamp(g, 0, 255) / 255;
  const bn = clamp(b, 0, 255) / 255;

  const max = Math.max(rn, gn, bn);
  const min = Math.min(rn, gn, bn);
  const delta = max - min;

  let h = 0;
  if (delta !== 0) {
    if (max === rn) h = 60 * (((gn - bn) / delta) % 6);
    else if (max === gn) h = 60 * ((bn - rn) / delta + 2);
    else h = 60 * ((rn - gn) / delta + 4);
  }
  if (h < 0) h += 360;

  const s = max === 0 ? 0 : delta / max;
  return { h: Math.round(h), s: Math.round(s * 100), v: Math.round(max * 100) };
}

export const rgbToCss = ({ r, g, b }: RGB) => `rgb(${r}, ${g}, ${b})`;

export function gradientCss(stops: RGB[], angle = 90): string {
  if (stops.length <= 1) return rgbToCss(stops[0] ?? { r: 0, g: 0, b: 0 });
  return `linear-gradient(${angle}deg, ${stops.map(rgbToCss).join(", ")})`;
}

export function expandGradient(stops: RGB[], count: number): RGB[] {
  const safe = stops.length ? stops : [{ r: 255, g: 255, b: 255 }];
  if (count <= 1) return [safe[0]];
  if (safe.length === 1) return Array.from({ length: count }, () => safe[0]);
  const segments = safe.length - 1;
  return Array.from({ length: count }, (_, i) => {
    const pos = (i / (count - 1)) * segments;
    const idx = Math.min(Math.floor(pos), segments - 1);
    const f = pos - idx;
    const a = safe[idx];
    const b = safe[idx + 1];
    return {
      r: Math.round(a.r + (b.r - a.r) * f),
      g: Math.round(a.g + (b.g - a.g) * f),
      b: Math.round(a.b + (b.b - a.b) * f),
    };
  });
}

export function dim({ r, g, b }: RGB, percent: number): RGB {
  const factor = clamp(percent, 0, 100) / 100;
  return {
    r: Math.round(r * factor),
    g: Math.round(g * factor),
    b: Math.round(b * factor),
  };
}

export function softenForDisplay({ r, g, b }: RGB, amount = 0.18): RGB {
  const mix = clamp(amount, 0, 1);
  return {
    r: Math.round(r + (255 - r) * mix),
    g: Math.round(g + (255 - g) * mix),
    b: Math.round(b + (255 - b) * mix),
  };
}
