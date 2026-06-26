import {
  PanelSection,
  PanelSectionRow,
  SliderField,
  ToggleField,
  Focusable,
  Spinner,
  staticClasses,
} from "@decky/ui";
import { definePlugin } from "@decky/api";
import { useEffect, useRef, useState } from "react";
import { FaPalette } from "react-icons/fa";

import { useColores } from "./useColores";
import { hsvToRgb, rgbToHsv, rgbToCss, HSV } from "./color";
import { RGB } from "./types";
import { DevicePreview } from "./components/DevicePreview";
import { Swatches } from "./components/Swatches";

const HUE_BAR =
  "linear-gradient(90deg, #ff0000, #ffff00, #00ff00, #00ffff, #0000ff, #ff00ff, #ff0000)";

function DeviceHeader({ name, color }: { name: string; color: RGB }) {
  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        gap: 10,
        padding: "2px 2px 8px",
      }}
    >
      <div
        style={{
          width: 12,
          height: 12,
          borderRadius: "50%",
          background: rgbToCss(color),
          boxShadow: `0 0 8px ${rgbToCss(color)}`,
        }}
      />
      <div style={{ fontWeight: 600, fontSize: 15 }}>{name}</div>
    </div>
  );
}

function GradientTrack({ background }: { background: string }) {
  return (
    <div
      style={{
        height: 6,
        borderRadius: 3,
        background,
        margin: "0 16px -6px",
        boxShadow: "inset 0 0 0 1px rgba(255,255,255,0.1)",
      }}
    />
  );
}

function Content() {
  const { state, setColor, setBrightness, setPower } = useColores();
  const [hsv, setHsv] = useState<HSV>({ h: 0, s: 100, v: 100 });
  const init = useRef(false);

  useEffect(() => {
    if (state && !init.current) {
      setHsv({ ...rgbToHsv(state.color), v: 100 });
      init.current = true;
    }
  }, [state]);

  if (!state) {
    return (
      <PanelSection>
        <PanelSectionRow>
          <div style={{ display: "flex", justifyContent: "center", padding: 20 }}>
            <Spinner width={32} height={32} />
          </div>
        </PanelSectionRow>
      </PanelSection>
    );
  }

  const { capabilities: caps, color, brightness, power, device } = state;
  const hasLeds = caps.color || caps.brightness;

  const editHsv = (next: HSV) => {
    setHsv(next);
    setColor(hsvToRgb(next.h, next.s, 100));
  };

  const pickPreset = (rgb: RGB) => {
    setHsv(rgbToHsv(rgb));
    setColor(rgb);
  };

  const satTrack = `linear-gradient(90deg, #808080, ${rgbToCss(hsvToRgb(hsv.h, 100, 100))})`;

  return (
    <PanelSection>
      <PanelSectionRow>
        <DeviceHeader name={device.name} color={color} />
      </PanelSectionRow>

      {!hasLeds && (
        <PanelSectionRow>
          <div style={{ fontSize: 13, color: "rgba(255,255,255,0.55)", padding: "4px 2px" }}>
            No controllable LEDs detected on this device.
          </div>
        </PanelSectionRow>
      )}

      {hasLeds && (
        <>
          <PanelSectionRow>
            <DevicePreview color={color} brightness={brightness} power={power} />
          </PanelSectionRow>

          <PanelSectionRow>
            <ToggleField
              label="Power"
              checked={power}
              onChange={setPower}
              bottomSeparator="thick"
            />
          </PanelSectionRow>
        </>
      )}

      {caps.color && (
        <>
          <PanelSectionRow>
            <Focusable style={{ paddingTop: 4 }}>
              <Swatches selected={color} onPick={pickPreset} />
            </Focusable>
          </PanelSectionRow>

          <PanelSectionRow>
            <GradientTrack background={HUE_BAR} />
            <SliderField
              label="Hue"
              value={hsv.h}
              min={0}
              max={360}
              step={1}
              disabled={!power}
              onChange={(h) => editHsv({ ...hsv, h })}
            />
          </PanelSectionRow>

          <PanelSectionRow>
            <GradientTrack background={satTrack} />
            <SliderField
              label="Saturation"
              value={hsv.s}
              min={0}
              max={100}
              step={1}
              valueSuffix="%"
              showValue
              disabled={!power}
              onChange={(s) => editHsv({ ...hsv, s })}
            />
          </PanelSectionRow>
        </>
      )}

      {caps.brightness && (
        <PanelSectionRow>
          <SliderField
            label="Brightness"
            value={brightness}
            min={0}
            max={100}
            step={1}
            valueSuffix="%"
            showValue
            disabled={!power}
            onChange={setBrightness}
          />
        </PanelSectionRow>
      )}
    </PanelSection>
  );
}

export default definePlugin(() => ({
  name: "Colores",
  titleView: <div className={staticClasses.Title}>Colores</div>,
  content: <Content />,
  icon: <FaPalette />,
  onDismount() {},
}));
