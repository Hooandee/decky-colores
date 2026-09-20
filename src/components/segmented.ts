import { CSSProperties } from "react";

import { theme } from "../theme";

export const segmentGroupStyle: CSSProperties = {
  display: "flex",
  gap: 4,
  padding: 4,
  ...theme.card,
};

export function segmentItemStyle(active: boolean): CSSProperties {
  return {
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    gap: 5,
    borderRadius: 10,
    fontSize: theme.font.body,
    fontWeight: active ? 600 : 400,
    color: active ? theme.color.textPrimary : theme.color.textMuted,
    background: active ? theme.color.accent : "transparent",
    cursor: "pointer",
    whiteSpace: "nowrap",
    overflow: "hidden",
    textOverflow: "ellipsis",
    transition: "background 140ms ease, color 140ms ease",
  };
}
