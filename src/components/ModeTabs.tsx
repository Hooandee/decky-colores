import { FC } from "react";
import { Focusable } from "@decky/ui";
import { Mode } from "../types";

interface ModeTabsProps {
  value: Mode;
  modes: Mode[];
  onChange: (m: Mode) => void;
}

const LABELS: Record<Mode, string> = {
  solid: "Solid",
  gradient: "Gradient",
  effect: "Effect",
  ambient: "Ambient",
};

const ACCENT = "#5b8cff";

export const ModeTabs: FC<ModeTabsProps> = ({ value, modes, onChange }) => (
  <Focusable
    style={{
      display: "grid",
      gridTemplateColumns: `repeat(${modes.length}, 1fr)`,
      gap: 4,
      padding: 4,
      borderRadius: 12,
      background: "rgba(255,255,255,0.04)",
      border: "1px solid rgba(255,255,255,0.08)",
    }}
  >
    {modes.map((mode) => {
      const active = mode === value;
      return (
        <Focusable
          key={mode}
          onActivate={() => onChange(mode)}
          onClick={() => onChange(mode)}
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            height: 30,
            borderRadius: 9,
            fontSize: 11.5,
            fontWeight: active ? 700 : 500,
            color: active ? "#fff" : "rgba(255,255,255,0.55)",
            background: active ? `linear-gradient(180deg, ${ACCENT}, #3f6ae0)` : "transparent",
            boxShadow: active
              ? `0 0 12px ${ACCENT}66, inset 0 0 0 1px rgba(255,255,255,0.18)`
              : "none",
            cursor: "pointer",
            transition: "background 140ms ease, box-shadow 140ms ease, color 140ms ease",
          }}
        >
          {LABELS[mode]}
        </Focusable>
      );
    })}
  </Focusable>
);
