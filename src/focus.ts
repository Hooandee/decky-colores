import { FALLBACK_ACCENT_RGB } from "./accent";

export const COLORES_ROOT = "colores-root";
export const FOCUS_STYLE_ID = "colores-focus-styles";

export function buildFocusCss(): string {
  const ring = `rgb(var(--colores-accent-rgb, ${FALLBACK_ACCENT_RGB}))`;
  const halo = `rgba(var(--colores-accent-rgb, ${FALLBACK_ACCENT_RGB}), 0.55)`;
  return `
.${COLORES_ROOT} .gpfocus {
  border-radius: 10px !important;
  box-shadow: 0 0 0 3px #0a0a0d,
              0 0 0 5px ${ring},
              0 0 11px 4px ${halo} !important;
  filter: brightness(1.05);
  transition: box-shadow 120ms ease, filter 120ms ease;
  position: relative;
  z-index: 1;
}`.trim();
}

export function ensureFocusStyles(doc: Document = document): void {
  try {
    const css = buildFocusCss();
    let el = doc.getElementById(FOCUS_STYLE_ID);
    if (!el) {
      el = doc.createElement("style");
      el.id = FOCUS_STYLE_ID;
      doc.head.appendChild(el);
    }
    if (el.textContent !== css) el.textContent = css;
  } catch {
    void 0;
  }
}
