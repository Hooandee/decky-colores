import { FALLBACK_ACCENT_RGB } from "./accent";

export const COLORES_ROOT = "colores-root";
export const FOCUS_STYLE_ID = "colores-focus-styles";
export const COLORES_TABSTRIP = "colores-tabstrip";

export function focusAfterNavigation(
  element: HTMLElement,
  scheduleFrame: (callback: FrameRequestCallback) => number = requestAnimationFrame,
  cancelFrame: (handle: number) => void = cancelAnimationFrame,
): () => void {
  const canFocus = () => element.isConnected && element.ownerDocument.hasFocus();
  if (canFocus()) element.focus();
  const handle = scheduleFrame(() => {
    if (canFocus()) element.focus();
  });
  return () => cancelFrame(handle);
}

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
}
.${COLORES_TABSTRIP} { scrollbar-width: none; -ms-overflow-style: none; }
.${COLORES_TABSTRIP}::-webkit-scrollbar { display: none; width: 0; height: 0; }`.trim();
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
