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
import { useEffect, useState } from "react";
import { FaPalette } from "react-icons/fa";

import { useColores } from "./useColores";
import { getAmbilightStatus } from "./api";
import { rgbToCss, gradientCss } from "./color";
import { Mode, RGB, ZoneGroup } from "./types";
import { DevicePreview } from "./components/DevicePreview";
import { ColorEditor } from "./components/ColorEditor";
import { ModeTabs } from "./components/ModeTabs";
import { EffectsGallery } from "./components/EffectsGallery";
import { GradientModal } from "./components/GradientModal";
import { GRADIENT_PRESETS, EFFECT_PRESETS } from "./palette";

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

const AMBIENT_HINT: RGB[] = [
  { r: 0, g: 196, b: 255 },
  { r: 124, g: 92, b: 255 },
];

function GradientControls({
  gradient,
  layout,
  onChange,
}: {
  gradient: RGB[];
  layout: ZoneGroup[];
  onChange: (stops: RGB[]) => void;
}) {
  const open = () =>
    showModal(<GradientModal initial={gradient} layout={layout} onApply={onChange} />);
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
    setColor,
    setGradient,
    setEffectId,
    setEffectSpeed,
    setAmbilight,
  } = useColores();
  const [ambStatus, setAmbStatus] = useState<string>("idle");

  const ambientActive = state?.mode === "ambient" && state?.power;
  useEffect(() => {
    if (!ambientActive) return;
    let alive = true;
    const poll = () =>
      getAmbilightStatus()
        .then((s) => alive && setAmbStatus(s))
        .catch(() => {});
    poll();
    const timer = setInterval(poll, 2000);
    return () => {
      alive = false;
      clearInterval(timer);
    };
  }, [ambientActive]);

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

  const modes: Mode[] = caps.ambilight
    ? ["solid", "gradient", "effect", "ambient"]
    : ["solid", "gradient", "effect"];

  const selectedEffect = EFFECT_PRESETS.find((e) => e.id === effect.id);

  const previewColors: RGB[] =
    mode === "gradient"
      ? gradient
      : mode === "effect"
        ? selectedEffect?.colors ?? [color]
        : mode === "ambient"
          ? AMBIENT_HINT
          : [color];

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
                <ColorEditor color={color} disabled={!power} onChange={setColor} />
              )}

              {mode === "gradient" && (
                <GradientControls gradient={gradient} layout={caps.layout} onChange={setGradient} />
              )}

              {mode === "effect" && (
                <>
                  <PanelSectionRow>
                    <EffectsGallery
                      effects={EFFECT_PRESETS}
                      selected={effect.id}
                      speed={effect.speed}
                      onSelect={setEffectId}
                      onSpeed={setEffectSpeed}
                    />
                  </PanelSectionRow>
                  {selectedEffect?.needs === "color" && (
                    <ColorEditor color={color} disabled={!power} onChange={setColor} />
                  )}
                  {selectedEffect?.needs === "gradient" && (
                    <GradientControls
                      gradient={gradient}
                      layout={caps.layout}
                      onChange={setGradient}
                    />
                  )}
                  {selectedEffect?.needs === "none" && (
                    <PanelSectionRow>
                      <div
                        style={{
                          fontSize: 12,
                          color: "rgba(255,255,255,0.5)",
                          padding: "4px 2px 8px",
                        }}
                      >
                        This effect uses the full color spectrum.
                      </div>
                    </PanelSectionRow>
                  )}
                </>
              )}

              {mode === "ambient" && (
                <>
                  {power && ambStatus === "no_source" && (
                    <PanelSectionRow>
                      <div
                        style={{
                          fontSize: 12,
                          color: "#ffcf66",
                          background: "rgba(255, 184, 0, 0.1)",
                          border: "1px solid rgba(255, 184, 0, 0.3)",
                          borderRadius: 10,
                          padding: "10px 12px",
                          lineHeight: 1.45,
                        }}
                      >
                        No screen to read yet. Ambient works in <b>Game Mode</b> with a game running
                        (not in Desktop / Big Picture).
                      </div>
                    </PanelSectionRow>
                  )}
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
                      onChange={(v) => setAmbilight(v, ambilight.smoothing, ambilight.fps)}
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
                      onChange={(v) => setAmbilight(ambilight.saturation, v, ambilight.fps)}
                    />
                  </PanelSectionRow>
                  <PanelSectionRow>
                    <SliderField
                      label="Capture rate"
                      value={ambilight.fps}
                      min={5}
                      max={30}
                      step={5}
                      valueSuffix=" fps"
                      showValue
                      disabled={!power}
                      onChange={(v) => setAmbilight(ambilight.saturation, ambilight.smoothing, v)}
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
