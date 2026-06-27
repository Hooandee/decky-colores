import { FC, useState } from "react";
import { PanelSectionRow, SliderField, Focusable } from "@decky/ui";
import { RGB } from "../types";
import { hsvToRgb, rgbToHsv, rgbToCss } from "../color";
import { Swatches } from "./Swatches";
import { useI18n } from "../i18n";

const HUE_BAR =
  "linear-gradient(90deg, #ff0000, #ffff00, #00ff00, #00ffff, #0000ff, #ff00ff, #ff0000)";

const Track: FC<{ background: string }> = ({ background }) => (
  <div
    style={{
      height: 6,
      borderRadius: 3,
      background,
      margin: "10px 16px 2px",
      boxShadow: "inset 0 0 0 1px rgba(255,255,255,0.1)",
    }}
  />
);

interface ColorEditorProps {
  color: RGB;
  disabled?: boolean;
  onChange: (color: RGB) => void;
}

export const ColorEditor: FC<ColorEditorProps> = ({ color, disabled, onChange }) => {
  const { t } = useI18n();
  const [hsv, setHsv] = useState(() => ({ ...rgbToHsv(color), v: 100 }));

  const edit = (next: { h: number; s: number; v: number }) => {
    setHsv(next);
    onChange(hsvToRgb(next.h, next.s, 100));
  };

  const pick = (rgb: RGB) => {
    setHsv({ ...rgbToHsv(rgb), v: 100 });
    onChange(rgb);
  };

  const satTrack = `linear-gradient(90deg, #808080, ${rgbToCss(hsvToRgb(hsv.h, 100, 100))})`;

  return (
    <>
      <PanelSectionRow>
        <Focusable style={{ padding: "6px 0 10px" }}>
          <Swatches selected={color} disabled={disabled} onPick={pick} />
        </Focusable>
      </PanelSectionRow>
      <PanelSectionRow>
        <Track background={HUE_BAR} />
        <SliderField
          label={t("color.hue")}
          value={hsv.h}
          min={0}
          max={360}
          step={1}
          disabled={disabled}
          onChange={(h) => edit({ ...hsv, h })}
        />
      </PanelSectionRow>
      <PanelSectionRow>
        <Track background={satTrack} />
        <SliderField
          label={t("color.saturation")}
          value={hsv.s}
          min={0}
          max={100}
          step={1}
          valueSuffix="%"
          showValue
          disabled={disabled}
          onChange={(s) => edit({ ...hsv, s })}
        />
      </PanelSectionRow>
    </>
  );
};
