import { hsvToRgb, rgbToHsv } from "./color";
import type { Lang } from "./i18n";
import { EffectMeta, GradientPreset, RGB, SensorBand, SensorBands, SensorKind } from "./types";

export const GRADIENT_PRESETS: GradientPreset[] = [
  {
    name: "Sunset",
    stops: [
      { r: 255, g: 94, b: 58 },
      { r: 255, g: 149, b: 0 },
      { r: 255, g: 45, b: 109 },
    ],
  },
  {
    name: "Ocean",
    stops: [
      { r: 0, g: 119, b: 182 },
      { r: 0, g: 180, b: 216 },
      { r: 144, g: 224, b: 239 },
    ],
  },
  {
    name: "Aurora",
    stops: [
      { r: 0, g: 201, b: 167 },
      { r: 92, g: 107, b: 192 },
      { r: 171, g: 71, b: 188 },
    ],
  },
  {
    name: "Neon",
    stops: [
      { r: 0, g: 255, b: 200 },
      { r: 255, g: 0, b: 230 },
    ],
  },
  {
    name: "Lava",
    stops: [
      { r: 255, g: 0, b: 0 },
      { r: 255, g: 102, b: 0 },
      { r: 255, g: 196, b: 0 },
    ],
  },
  {
    name: "Mint",
    stops: [
      { r: 67, g: 233, b: 123 },
      { r: 56, g: 249, b: 215 },
    ],
  },
  {
    name: "Vaporwave",
    stops: [
      { r: 255, g: 113, b: 206 },
      { r: 120, g: 115, b: 245 },
      { r: 1, g: 205, b: 254 },
    ],
  },
  {
    name: "Forest",
    stops: [
      { r: 19, g: 78, b: 94 },
      { r: 113, g: 178, b: 128 },
      { r: 221, g: 235, b: 157 },
    ],
  },
  {
    name: "Galaxy",
    stops: [
      { r: 30, g: 27, b: 75 },
      { r: 91, g: 33, b: 182 },
      { r: 219, g: 39, b: 119 },
    ],
  },
  {
    name: "Ember",
    stops: [
      { r: 64, g: 9, b: 9 },
      { r: 200, g: 40, b: 20 },
      { r: 255, g: 138, b: 76 },
    ],
  },
  {
    name: "Ice",
    stops: [
      { r: 161, g: 196, b: 253 },
      { r: 194, g: 233, b: 251 },
    ],
  },
  {
    name: "Candy",
    stops: [
      { r: 255, g: 154, b: 158 },
      { r: 250, g: 208, b: 196 },
      { r: 255, g: 102, b: 196 },
    ],
  },
];

export const EFFECT_PRESETS: EffectMeta[] = [
  {
    id: "breathing",
    label: "Breathing",
    needs: "color",
    description: "A single color gently fades in and out.",
    colors: [
      { r: 124, g: 77, b: 255 },
      { r: 38, g: 23, b: 79 },
    ],
  },
  {
    id: "rainbow",
    label: "Rainbow",
    needs: "none",
    description: "Smoothly cycles through the full color spectrum.",
    colors: [
      { r: 255, g: 0, b: 0 },
      { r: 0, g: 255, b: 0 },
      { r: 0, g: 0, b: 255 },
    ],
  },
  {
    id: "wave",
    label: "Wave",
    needs: "gradient",
    description: "Colors ripple across the ring like a moving wave.",
    colors: [
      { r: 0, g: 180, b: 216 },
      { r: 144, g: 224, b: 239 },
      { r: 0, g: 119, b: 182 },
    ],
  },
  {
    id: "cycle",
    label: "Cycle",
    needs: "none",
    description: "Steps through a sequence of vivid solid colors.",
    colors: [
      { r: 255, g: 64, b: 129 },
      { r: 255, g: 196, b: 0 },
      { r: 41, g: 182, b: 246 },
    ],
  },
  {
    id: "spiral",
    label: "Spiral",
    needs: "gradient",
    description: "A gradient that spins around the ring.",
    colors: [
      { r: 255, g: 0, b: 128 },
      { r: 124, g: 92, b: 255 },
      { r: 0, g: 196, b: 255 },
      { r: 64, g: 224, b: 120 },
    ],
  },
  {
    id: "comet",
    label: "Comet",
    needs: "color",
    description: "A bright dot sweeps back and forth with a fading tail.",
    colors: [
      { r: 0, g: 196, b: 255 },
      { r: 124, g: 92, b: 255 },
    ],
  },
  {
    id: "sparkle",
    label: "Sparkle",
    needs: "color",
    description: "LEDs twinkle at random over a dim base.",
    colors: [
      { r: 255, g: 255, b: 255 },
      { r: 124, g: 180, b: 255 },
    ],
  },
  {
    id: "ripple",
    label: "Ripple",
    needs: "color",
    description: "A gentle brightness wave glides softly along the bar.",
    colors: [
      { r: 0, g: 180, b: 216 },
      { r: 144, g: 224, b: 239 },
    ],
  },
  {
    id: "aurora",
    label: "Aurora",
    needs: "none",
    description: "Flowing greens, blues and purples drift like an aurora.",
    colors: [
      { r: 40, g: 224, b: 140 },
      { r: 0, g: 160, b: 220 },
      { r: 138, g: 92, b: 255 },
    ],
  },
];

export const BATTERY_BANDS: SensorBand[] = [
  { min: 81, color: { r: 0, g: 120, b: 255 } },
  { min: 61, color: { r: 0, g: 200, b: 60 } },
  { min: 41, color: { r: 255, g: 200, b: 0 } },
  { min: 21, color: { r: 255, g: 110, b: 0 } },
  { min: 0, color: { r: 255, g: 30, b: 20 } },
];

export const TEMPERATURE_BANDS: SensorBand[] = [
  { min: 90, color: { r: 255, g: 30, b: 20 } },
  { min: 80, color: { r: 255, g: 110, b: 0 } },
  { min: 68, color: { r: 255, g: 200, b: 0 } },
  { min: 55, color: { r: 0, g: 200, b: 60 } },
  { min: 0, color: { r: 0, g: 120, b: 255 } },
];

export const TEMPERATURE_RANGE = { min: 40, max: 95 };

export const DEFAULT_SENSOR_BANDS: SensorBands = {
  battery: BATTERY_BANDS,
  temperature: TEMPERATURE_BANDS,
};

const CLOCK_KEYS: [number, RGB][] = [
  [0, { r: 12, g: 22, b: 64 }],
  [6, { r: 255, g: 120, b: 40 }],
  [9, { r: 170, g: 205, b: 255 }],
  [14, { r: 255, g: 248, b: 230 }],
  [18, { r: 255, g: 105, b: 40 }],
  [21, { r: 40, g: 28, b: 92 }],
  [24, { r: 12, g: 22, b: 64 }],
];

export function clockColor(hour: number): RGB {
  const h = ((hour % 24) + 24) % 24;
  for (let i = 0; i < CLOCK_KEYS.length - 1; i++) {
    const [h0, c0] = CLOCK_KEYS[i];
    const [h1, c1] = CLOCK_KEYS[i + 1];
    if (h >= h0 && h <= h1) {
      const f = h1 > h0 ? (h - h0) / (h1 - h0) : 0;
      return { r: lerpChannel(c0.r, c1.r, f), g: lerpChannel(c0.g, c1.g, f), b: lerpChannel(c0.b, c1.b, f) };
    }
  }
  return CLOCK_KEYS[0][1];
}

export const PERFORMANCE_STOPS: RGB[] = [
  { r: 0, g: 230, b: 90 },
  { r: 255, g: 200, b: 0 },
  { r: 255, g: 40, b: 0 },
];

function lerpChannel(a: number, b: number, f: number): number {
  return roundHalfEven(a + (b - a) * f);
}

function roundHalfEven(value: number): number {
  const lower = Math.floor(value);
  const fraction = value - lower;
  if (Math.abs(fraction - 0.5) < Number.EPSILON * Math.max(1, Math.abs(value))) {
    return lower % 2 === 0 ? lower : lower + 1;
  }
  return Math.round(value);
}

function sampleRamp(stops: RGB[], pos: number): RGB {
  if (stops.length === 1) return stops[0];
  const p = Math.max(0, Math.min(1, pos)) * (stops.length - 1);
  const lo = Math.min(stops.length - 2, Math.floor(p));
  const f = p - lo;
  const a = stops[lo];
  const b = stops[lo + 1];
  return { r: lerpChannel(a.r, b.r, f), g: lerpChannel(a.g, b.g, f), b: lerpChannel(a.b, b.b, f) };
}

function fillBar(
  zones: number,
  fillOf: (i: number, n: number) => number,
  posOf: (i: number, n: number) => number,
): RGB[] {
  const n = Math.max(1, zones);
  return Array.from({ length: n }, (_, i) => {
    const fill = Math.max(0, Math.min(1, fillOf(i, n)));
    const ramp = sampleRamp(PERFORMANCE_STOPS, posOf(i, n));
    return {
      r: roundHalfEven(ramp.r * fill),
      g: roundHalfEven(ramp.g * fill),
      b: roundHalfEven(ramp.b * fill),
    };
  });
}

export function performanceMeterColors(loadPct: number, zones: number): RGB[] {
  const lit = (Math.max(0, Math.min(100, loadPct)) / 100) * Math.max(1, zones);
  return fillBar(zones, (i) => lit - i, (i, n) => (n > 1 ? i / (n - 1) : 0));
}

export function audioVuColors(level: number, zones: number): RGB[] {
  if (zones <= 0) return [];
  const n = zones;
  const center = (n - 1) / 2;
  const centerFill = n % 2 === 0 ? 0.5 : 0;
  const radius = center - centerFill;
  const reach = Math.max(0, Math.min(1, level)) * (n / 2);
  return fillBar(
    zones,
    (i) => reach + centerFill - Math.abs(i - center),
    (i) => (radius ? Math.max(0, Math.abs(i - center) - centerFill) / radius : 0),
  );
}

export function sensorBandColor(value: number, bands: SensorBand[]): RGB {
  for (const band of bands) {
    if (value >= band.min) return band.color;
  }
  return bands[bands.length - 1].color;
}

export function sensorThresholdBounds(
  sensor: SensorKind,
  index: number,
  bands: SensorBand[],
): { min: number; max: number } | null {
  if (index < 0 || index >= bands.length - 1) return null;
  return {
    min: bands[index + 1].min + 1,
    max: index === 0 ? (sensor === "battery" ? 100 : 120) : bands[index - 1].min - 1,
  };
}

export function sensorScaleRange(sensor: SensorKind, bands: SensorBand[]) {
  if (sensor === "battery") return { min: 0, max: 100 };
  return {
    min: Math.min(TEMPERATURE_RANGE.min, bands[bands.length - 2]?.min ?? TEMPERATURE_RANGE.min),
    max: Math.max(TEMPERATURE_RANGE.max, bands[0]?.min ?? TEMPERATURE_RANGE.max),
  };
}

export function sensorScalePosition(
  sensor: SensorKind,
  bands: SensorBand[],
  value: number,
): number {
  const range = sensorScaleRange(sensor, bands);
  return Math.max(0, Math.min(100, ((value - range.min) / (range.max - range.min)) * 100));
}

export function formatSensorValue(
  value: number,
  lang: Lang,
  fractionDigits = 1,
): string {
  return value.toLocaleString(lang, {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  });
}

export function sensorThresholdPositions(sensor: SensorKind, bands: SensorBand[]): number[] {
  return bands.slice(0, -1).map((band) => sensorScalePosition(sensor, bands, band.min));
}

const NAME_PARTS: Record<Lang, { adjectives: string[]; nouns: string[] }> = {
  es: {
    adjectives: ["Borracho", "Eléctrico", "Cósmico", "Disco", "Pastel", "Neón", "Salvaje", "Turbo", "Místico", "Picante"],
    nouns: ["Atardecer", "Unicornio", "Mango", "Pulpo", "Trueno", "Gato", "Dragón", "Flamenco", "Tucán", "Cactus"],
  },
  en: {
    adjectives: ["Drunken", "Electric", "Cosmic", "Disco", "Pastel", "Neon", "Wild", "Turbo", "Mystic", "Spicy"],
    nouns: ["Sunset", "Unicorn", "Mango", "Octopus", "Thunder", "Cat", "Dragon", "Flamingo", "Toucan", "Cactus"],
  },
  it: {
    adjectives: ["elettrico", "cosmico", "disco", "pastello", "neon", "selvaggio", "turbo", "mistico", "piccante", "sognante"],
    nouns: ["Tramonto", "Unicorno", "Mango", "Polpo", "Tuono", "Gatto", "Drago", "Fenicottero", "Tucano", "Cactus"],
  },
};

export function suggestGradientName(lang: Lang): string {
  const parts = NAME_PARTS[lang] ?? NAME_PARTS.en;
  const noun = parts.nouns[Math.floor(Math.random() * parts.nouns.length)];
  const adjective = parts.adjectives[Math.floor(Math.random() * parts.adjectives.length)];
  return lang === "en" ? `${adjective} ${noun}` : `${noun} ${adjective}`;
}

export function harmoniousGradient(base: RGB): RGB[] {
  const { h } = rgbToHsv(base);
  const s = 85;
  const v = 95;
  return [hsvToRgb(h - 30, s, v), hsvToRgb(h, s, v), hsvToRgb(h + 30, s, v)];
}

export function randomGradient(): RGB[] {
  const baseHue = Math.random() * 360;
  const s = 80 + Math.random() * 20;
  const v = 85 + Math.random() * 15;

  if (Math.random() < 0.5) {
    return [hsvToRgb(baseHue, s, v), hsvToRgb(baseHue + 180, s, v)];
  }

  return [
    hsvToRgb(baseHue - 30, s, v),
    hsvToRgb(baseHue, s, v),
    hsvToRgb(baseHue + 30, s, v),
  ];
}
