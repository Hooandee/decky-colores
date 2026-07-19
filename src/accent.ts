export interface Accent {
  id: string;
  hex: string;
}

export const ACCENTS: Accent[] = [
  { id: "blue", hex: "#5b8cff" },
  { id: "teal", hex: "#22c7c0" },
  { id: "green", hex: "#3fbf6f" },
  { id: "amber", hex: "#e0952a" },
  { id: "orange", hex: "#f2683c" },
  { id: "pink", hex: "#ec5c9d" },
  { id: "purple", hex: "#9b7bf0" },
  { id: "red", hex: "#e5484d" },
];

export const DEFAULT_ACCENT = ACCENTS[0];
export const FALLBACK_ACCENT_RGB = "91,140,255";

export function resolveAccent(id: string | null | undefined): Accent {
  return ACCENTS.find((a) => a.id === id) ?? DEFAULT_ACCENT;
}

export function hexToRgbTriplet(hex: string): string {
  const h = hex.replace(/^#/, "");
  if (!/^[0-9a-fA-F]{6}$/.test(h)) return FALLBACK_ACCENT_RGB;
  return `${parseInt(h.slice(0, 2), 16)},${parseInt(h.slice(2, 4), 16)},${parseInt(h.slice(4, 6), 16)}`;
}

let current = DEFAULT_ACCENT;
const listeners = new Set<() => void>();

export function getAccentId(): string {
  return current.id;
}

export function applyAccentId(id: string): void {
  const next = resolveAccent(id);
  if (next.id === current.id) return;
  current = next;
  listeners.forEach((l) => l());
}

export function subscribeAccent(cb: () => void): () => void {
  listeners.add(cb);
  return () => {
    listeners.delete(cb);
  };
}
