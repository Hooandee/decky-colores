import {
  PanelSection,
  PanelSectionRow,
  SliderField,
  ToggleField,
  Focusable,
  Spinner,
  ErrorBoundary,
  showModal,
  staticClasses,
} from "@decky/ui";
import { definePlugin } from "@decky/api";
import { useEffect, useMemo, useRef, useState } from "react";
import { FaPalette } from "react-icons/fa";

import { useColores } from "./useColores";
import { hsvToRgb, rgbToHsv, rgbToCss, gradientCss } from "./color";
import { Mode, RGB } from "./types";
import { DevicePreview } from "./components/DevicePreview";
import { Swatches } from "./components/Swatches";
import { ModeTabs } from "./components/ModeTabs";
import { EffectsGallery } from "./components/EffectsGallery";
import { GradientModal } from "./components/GradientModal";
import { GRADIENT_PRESETS, EFFECT_PRESETS } from "./palette";

const HUE_BAR =
  "linear-gradient(90deg, #ff0000, #ffff00, #00ff00, #00ffff, #0000ff, #ff00ff, #ff0000)";

function DeviceHeader({ name, color }: { name: string; color: RGB }) {
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 10, padding: "4px 2px 16px" }}>
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
        margin: "10px 16px 2px",
        boxShadow: "inset 0 0 0 1px rgba(255,255,255,0.1)",
      }}
    />
  );
}

const AMBIENT_HINT: RGB[] = [
  { r: 0, g: 196, b: 255 },
  { r: 124, g: 92, b: 255 },
];

function GradientControls({
  gradient,
  zones,
  onChange,
}: {
  gradient: RGB[];
  zones: number;
  onChange: (stops: RGB[]) => void;
}) {
  const open = () =>
    showModal(<GradientModal initial={gradient} zones={zones} onApply={onChange} />);
  return (
    <>
      <PanelSectionRow>
        <Focusable
          onActivate={open}
          onClick={open}
          style={{
            height: 46,
            borderRadius: 12,
            background: gradientCss(gradient),
            boxShadow: "inset 0 0 0 1px rgba(255,255,255,0.12)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            cursor: "pointer",
          }}
        >
          <span
            style={{
              fontWeight: 600,
              fontSize: 13,
              color: "#fff",
              textShadow: "0 1px 3px rgba(0,0,0,0.6)",
            }}
          >
            Edit gradient
          </span>
        </Focusable>
      </PanelSectionRow>
      <PanelSectionRow>
        <Focusable
          style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 6, paddingTop: 4 }}
        >
          {GRADIENT_PRESETS.map((preset) => (
            <Focusable
              key={preset.name}
              onActivate={() => onChange(preset.stops)}
              onClick={() => onChange(preset.stops)}
              style={{
                height: 30,
                borderRadius: 8,
                background: gradientCss(preset.stops),
                boxShadow: "inset 0 0 0 1px rgba(255,255,255,0.12)",
                cursor: "pointer",
              }}
            >
              <div style={{ width: "100%", height: "100%" }} />
            </Focusable>
          ))}
        </Focusable>
      </PanelSectionRow>
    </>
  );
}

function Content() {
  const {
    state,
    setBrightness,
    setPower,
    setMode,
    setSolid,
    setGradient,
    setEffectId,
    setEffectSpeed,
    setAmbilight,
  } = useColores();
  const [hsv, setHsv] = useState({ h: 0, s: 100, v: 100 });
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

  const { capabilities: caps, color, gradient, effect, ambilight, brightness, power, mode, device } =
    state;
  const hasLeds = caps.color || caps.brightness;

  const modes = useMemo<Mode[]>(
    () => (caps.ambilight ? ["solid", "gradient", "effect", "ambient"] : ["solid", "gradient", "effect"]),
    [caps.ambilight],
  );

  const previewColors = useMemo<RGB[]>(() => {
    if (mode === "gradient") return gradient;
    if (mode === "effect") return EFFECT_PRESETS.find((e) => e.id === effect.id)?.colors ?? [color];
    if (mode === "ambient") return AMBIENT_HINT;
    return [color];
  }, [mode, gradient, effect.id, color]);

  const editHsv = (next: { h: number; s: number; v: number }) => {
    setHsv(next);
    setSolid(hsvToRgb(next.h, next.s, 100));
  };

  const pickPreset = (rgb: RGB) => {
    setHsv({ ...rgbToHsv(rgb), v: 100 });
    setSolid(rgb);
  };

  const satTrack = `linear-gradient(90deg, #808080, ${rgbToCss(hsvToRgb(hsv.h, 100, 100))})`;

  return (
    <PanelSection>
      <PanelSectionRow>
        <DeviceHeader name={device.name} color={previewColors[0]} />
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
            <DevicePreview
              colors={previewColors}
              brightness={brightness}
              power={power}
              label={mode === "ambient" ? "Reacting to screen" : undefined}
            />
          </PanelSectionRow>

          <PanelSectionRow>
            <ToggleField label="Power" checked={power} onChange={setPower} bottomSeparator="thick" />
          </PanelSectionRow>

          {caps.color && (
            <>
              <PanelSectionRow>
                <div style={{ margin: "8px 0 14px" }}>
                  <ModeTabs value={mode} modes={modes} onChange={setMode} />
                </div>
              </PanelSectionRow>

              {mode === "solid" && (
                <>
                  <PanelSectionRow>
                    <Focusable style={{ padding: "6px 0 10px" }}>
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

              {mode === "gradient" && (
                <GradientControls gradient={gradient} zones={caps.zones} onChange={setGradient} />
              )}

              {mode === "effect" && (
                <PanelSectionRow>
                  <EffectsGallery
                    effects={EFFECT_PRESETS}
                    selected={effect.id}
                    speed={effect.speed}
                    onSelect={setEffectId}
                    onSpeed={setEffectSpeed}
                  />
                </PanelSectionRow>
              )}

              {mode === "ambient" && (
                <>
                  <PanelSectionRow>
                    <div
                      style={{
                        fontSize: 12,
                        color: "rgba(255,255,255,0.55)",
                        padding: "4px 2px 12px",
                        lineHeight: 1.45,
                      }}
                    >
                      Lights follow the screen near each stick — left from the top-left, right from
                      the mid-right.
                    </div>
                  </PanelSectionRow>
                  <PanelSectionRow>
                    <SliderField
                      label="Vividness"
                      value={ambilight.saturation}
                      min={100}
                      max={250}
                      step={5}
                      valueSuffix="%"
                      showValue
                      disabled={!power}
                      onChange={(v) => setAmbilight(v, ambilight.smoothing)}
                    />
                  </PanelSectionRow>
                  <PanelSectionRow>
                    <SliderField
                      label="Smoothing"
                      value={ambilight.smoothing}
                      min={0}
                      max={100}
                      step={1}
                      valueSuffix="%"
                      showValue
                      disabled={!power}
                      onChange={(v) => setAmbilight(ambilight.saturation, v)}
                    />
                  </PanelSectionRow>
                </>
              )}
            </>
          )}

          {caps.brightness && (
            <>
              <PanelSectionRow>
                <div
                  style={{ height: 1, background: "rgba(255,255,255,0.07)", margin: "14px 0 8px" }}
                />
              </PanelSectionRow>
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
            </>
          )}
        </>
      )}
    </PanelSection>
  );
}

export default definePlugin(() => ({
  name: "Colores",
  titleView: <div className={staticClasses.Title}>Colores</div>,
  content: (
    <ErrorBoundary>
      <Content />
    </ErrorBoundary>
  ),
  icon: <FaPalette />,
  onDismount() {},
}));
