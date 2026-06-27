import { FC } from "react";
import { Focusable } from "@decky/ui";
import { RGB } from "../types";
import { rgbToCss } from "../color";

const PRESETS: RGB[] = [
  { r: 255, g: 38, b: 56 },
  { r: 255, g: 122, b: 0 },
  { r: 255, g: 214, b: 0 },
  { r: 64, g: 224, b: 120 },
  { r: 0, g: 196, b: 255 },
  { r: 88, g: 86, b: 255 },
  { r: 200, g: 70, b: 255 },
  { r: 255, g: 255, b: 255 },
];

interface SwatchesProps {
  selected: RGB;
  disabled?: boolean;
  onPick: (color: RGB) => void;
}

const isSame = (a: RGB, b: RGB) => a.r === b.r && a.g === b.g && a.b === b.b;

export const Swatches: FC<SwatchesProps> = ({ selected, disabled, onPick }) => (
  <Focusable
    style={{
      display: "grid",
      gridTemplateColumns: "repeat(8, 1fr)",
      gap: 6,
      padding: "2px 0",
      opacity: disabled ? 0.4 : 1,
    }}
  >
    {PRESETS.map((preset, i) => {
      const active = isSame(preset, selected);
      return (
        <Focusable
          key={i}
          onActivate={() => !disabled && onPick(preset)}
          onClick={() => !disabled && onPick(preset)}
          style={{
            aspectRatio: "1 / 1",
            borderRadius: 8,
            background: rgbToCss(preset),
            boxShadow: active
              ? `0 0 0 2px #fff, 0 0 10px ${rgbToCss(preset)}`
              : "inset 0 0 0 1px rgba(255,255,255,0.12)",
            cursor: disabled ? "default" : "pointer",
            transition: "box-shadow 120ms ease, transform 120ms ease",
          }}
        >
          <div style={{ width: "100%", height: "100%" }} />
        </Focusable>
      );
    })}
  </Focusable>
);
