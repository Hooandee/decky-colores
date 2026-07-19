import { CSSProperties } from "react";
import { FALLBACK_ACCENT_RGB } from "../accent";

const ACCENT = `rgb(var(--colores-accent-rgb, ${FALLBACK_ACCENT_RGB}))`;

export const segmentGroupStyle: CSSProperties = {
  display: "flex",
  gap: 4,
  padding: 4,
  borderRadius: 12,
  background: "rgba(255,255,255,0.04)",
  boxShadow: "inset 0 0 0 1px rgba(255,255,255,0.07)",
};

export function segmentItemStyle(active: boolean): CSSProperties {
  return {
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    gap: 5,
    borderRadius: 8,
    fontSize: 13,
    fontWeight: active ? 600 : 400,
    color: active ? "#fff" : "rgba(255,255,255,0.6)",
    background: active ? ACCENT : "transparent",
    cursor: "pointer",
    whiteSpace: "nowrap",
    overflow: "hidden",
    textOverflow: "ellipsis",
    transition: "background 140ms ease, color 140ms ease",
  };
}
