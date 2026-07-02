import {
  PanelSection,
  PanelSectionRow,
  SliderField,
  ToggleField,
  ButtonItem,
  Focusable,
  Spinner,
  ErrorBoundary,
  showModal,
  staticClasses,
} from "@decky/ui";
import { definePlugin } from "@decky/api";
import { useEffect, useMemo, useState } from "react";

import { useColores } from "./useColores";
import { getAmbilightStatus, reconnect as apiReconnect } from "./api";
import { rgbToCss, gradientCss } from "./color";
import { Mode, RGB, ZoneGroup, GradientPreset, EffectColorNeed } from "./types";
import { DevicePreview } from "./components/DevicePreview";
import { ColorEditor } from "./components/ColorEditor";
import { ModeTabs } from "./components/ModeTabs";
import { EffectsGallery } from "./components/EffectsGallery";
import { GradientModal } from "./components/GradientModal";
import { About } from "./components/About";
import { ColorWheelIcon } from "./components/ColorWheelIcon";
import { GRADIENT_PRESETS, EFFECT_PRESETS, BATTERY_BANDS, batteryBandColor } from "./palette";
import { I18nProvider, LangToggle, useI18n } from "./i18n";

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
  crossfade,
  savedGradients,
  disabled,
  onChange,
  onSave,
  onDelete,
  speed,
  onSpeed,
}: {
  gradient: RGB[];
  layout: ZoneGroup[];
  crossfade?: boolean;
  savedGradients: GradientPreset[];
  disabled?: boolean;
  onChange: (stops: RGB[]) => void;
  onSave: (name: string, stops: RGB[]) => void;
  onDelete: (name: string) => void;
  speed?: number;
  onSpeed?: (v: number) => void;
}) {
  const { t } = useI18n();
  const allPresets = useMemo(
    () => [...GRADIENT_PRESETS, ...savedGradients],
    [savedGradients],
  );
  const open = () => {
    if (disabled) return;
    showModal(
      <GradientModal
        initial={gradient}
        layout={layout}
        crossfade={crossfade}
        savedGradients={savedGradients}
        onApply={onChange}
        onSave={onSave}
        onDelete={onDelete}
      />,
    );
  };
  return (
    <div style={{ opacity: disabled ? 0.4 : 1 }}>
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
            cursor: disabled ? "default" : "pointer",
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
            {t("gradient.edit")}
          </span>
        </Focusable>
      </PanelSectionRow>
      <PanelSectionRow>
        <Focusable
          style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 6, paddingTop: 4 }}
        >
          {allPresets.map((preset) => (
            <Focusable
              key={preset.name}
              onActivate={() => !disabled && onChange(preset.stops)}
              onClick={() => !disabled && onChange(preset.stops)}
              style={{
                height: 30,
                borderRadius: 8,
                background: gradientCss(preset.stops),
                boxShadow: "inset 0 0 0 1px rgba(255,255,255,0.12)",
                cursor: disabled ? "default" : "pointer",
              }}
            >
              <div style={{ width: "100%", height: "100%" }} />
            </Focusable>
          ))}
        </Focusable>
      </PanelSectionRow>
      {onSpeed && (
        <PanelSectionRow>
          <SliderField
            label={t("gradient.speed")}
            value={speed ?? 30}
            min={0}
            max={100}
            step={1}
            valueSuffix="%"
            showValue
            disabled={disabled}
            onChange={onSpeed}
          />
        </PanelSectionRow>
      )}
    </div>
  );
}

function EffectSource({
  kind,
  color,
  gradient,
}: {
  kind: "color" | "gradient";
  color: RGB;
  gradient: RGB[];
}) {
  const { t } = useI18n();
  const bg = kind === "gradient" ? gradientCss(gradient) : rgbToCss(color);
  return (
    <>
      <PanelSectionRow>
        <div
          style={{
            height: 34,
            borderRadius: 10,
            background: bg,
            boxShadow: "inset 0 0 0 1px rgba(255,255,255,0.12)",
          }}
        />
      </PanelSectionRow>
      <PanelSectionRow>
        <div
          style={{
            fontSize: 12,
            color: "rgba(255,255,255,0.55)",
            padding: "2px 2px 6px",
            lineHeight: 1.45,
          }}
        >
          {kind === "gradient" ? t("effect.usesGradient") : t("effect.usesColor")}
        </div>
      </PanelSectionRow>
    </>
  );
}

function BatteryPanel({
  level,
  breathe,
  disabled,
  onBreathe,
}: {
  level: number;
  breathe: boolean;
  disabled?: boolean;
  onBreathe: (on: boolean) => void;
}) {
  const { t } = useI18n();
  // Left (0%) -> right (100%): red ... blue. BATTERY_BANDS is high-to-low, so reverse.
  const barStops = [...BATTERY_BANDS].reverse().map((b) => b.color);
  const marker = Math.max(0, Math.min(100, level));
  return (
    <>
      <PanelSectionRow>
        <div
          style={{
            fontSize: 12,
            color: "rgba(255,255,255,0.55)",
            padding: "4px 2px 10px",
            lineHeight: 1.45,
          }}
        >
          {t("battery.hint")}
        </div>
      </PanelSectionRow>
      <PanelSectionRow>
        <div style={{ padding: "2px 2px 4px" }}>
          <div
            style={{
              position: "relative",
              height: 14,
              borderRadius: 7,
              background: gradientCss(barStops),
              boxShadow: "inset 0 0 0 1px rgba(255,255,255,0.12)",
            }}
          >
            <div
              style={{
                position: "absolute",
                top: -3,
                bottom: -3,
                left: `calc(${marker}% - 2px)`,
                width: 4,
                borderRadius: 2,
                background: "#fff",
                boxShadow: "0 0 4px rgba(0,0,0,0.6)",
              }}
            />
          </div>
          <div
            style={{
              display: "flex",
              justifyContent: "space-between",
              fontSize: 11,
              color: "rgba(255,255,255,0.45)",
              padding: "6px 1px 0",
            }}
          >
            <span>0%</span>
            <span style={{ color: "rgba(255,255,255,0.8)", fontWeight: 600 }}>
              {t("battery.level", { n: marker })}
            </span>
            <span>100%</span>
          </div>
        </div>
      </PanelSectionRow>
      <PanelSectionRow>
        <ToggleField
          label={t("battery.breathe.label")}
          description={t("battery.breathe.hint")}
          checked={breathe}
          disabled={disabled}
          onChange={onBreathe}
        />
      </PanelSectionRow>
    </>
  );
}

function Content() {
  const {
    state,
    loadError,
    retry,
    setBrightness,
    setPower,
    setChargerOnly,
    setMode,
    setColor,
    setGradient,
    setGradientSpeed,
    setEffectId,
    setEffectSpeed,
    setEffectGradient,
    setAmbilight,
    saveGradient,
    deleteGradient,
    setExperiment,
    setPowerLed,
    setForceControl,
    setBatteryBreathe,
    reconnect,
  } = useColores();
  const { t } = useI18n();
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

  // When Force control is on (only exposed on devices where another tool, e.g. HHD,
  // owns the RGB), reclaim the LEDs whenever the panel opens. reconnect() re-opens the
  // HID handle and re-applies from a clean state, forcing the Aura init handshake +
  // apply so the reclaim holds even if another tool grabbed the device. No polling.
  useEffect(() => {
    if (!state?.capabilities.conflictsWithSystemRgb || !state?.forceControl) return;
    apiReconnect().catch(() => {});
  }, [state?.capabilities.conflictsWithSystemRgb, state?.forceControl]);

  if (!state) {
    return (
      <PanelSection>
        {loadError ? (
          <>
            <PanelSectionRow>
              <div
                style={{
                  fontSize: 13,
                  color: "rgba(255,255,255,0.7)",
                  padding: "8px 2px",
                  lineHeight: 1.45,
                }}
              >
                {t("load.error")}
              </div>
            </PanelSectionRow>
            <PanelSectionRow>
              <ButtonItem layout="below" onClick={() => retry()}>
                {t("load.retry")}
              </ButtonItem>
            </PanelSectionRow>
          </>
        ) : (
          <PanelSectionRow>
            <div style={{ display: "flex", justifyContent: "center", padding: 20 }}>
              <Spinner width={32} height={32} />
            </div>
          </PanelSectionRow>
        )}
      </PanelSection>
    );
  }

  const {
    capabilities: caps,
    color,
    gradient,
    gradientSpeed,
    effect,
    ambilight,
    brightness,
    power,
    mode,
    device,
    savedGradients,
    powerLedOff,
    chargerOnly,
    forceControl,
    batteryBreathe,
    batteryLevel,
  } = state;
  const hasLeds = caps.color || caps.brightness;

  // Any colour device offers a gradient: per-zone devices render it spatially,
  // single-colour devices (Legion Go S / Go 2) crossfade through the palette over
  // time — the same gradient experience on every Legion Go.
  const canGradient = caps.color && caps.zones >= 1;
  // Devices that can't render a spatial gradient (single-color zones, e.g. Legion
  // rings) animate a crossfade through the palette, so they expose a speed control.
  const gradientAnimated = canGradient && !caps.perZone;

  const modes: Mode[] = (() => {
    const base: Mode[] = canGradient ? ["solid", "gradient", "effect"] : ["solid", "effect"];
    if (caps.batteryMode) base.push("battery");
    if (caps.ambilight) base.push("ambient");
    return base;
  })();

  const visibleEffects = caps.supportedEffects.length > 0
    ? EFFECT_PRESETS.filter((e) => caps.supportedEffects.includes(e.id))
    : EFFECT_PRESETS;

  const selectedEffect = visibleEffects.find((e) => e.id === effect.id) ?? visibleEffects[0];

  // Devices with native firmware effects (Legion Go) run spiral as their own
  // fixed-palette effect — labelled "Spiral GO", taking no user color. Devices
  // that paint zones in software (Ally) spin the user's gradient instead.
  const firmwareSpiral = caps.hardwareEffects;
  const isFirmwareSpiral = selectedEffect?.id === "spiral" && firmwareSpiral;
  const effectNeed: EffectColorNeed = isFirmwareSpiral
    ? "none"
    : selectedEffect?.needs === "gradient" && !canGradient
      ? "color"
      : (selectedEffect?.needs ?? "none");

  const effectPreview = (): RGB[] => {
    if (!selectedEffect || effectNeed === "none") return selectedEffect?.colors ?? [color];
    if (effectNeed === "gradient" || effect.useGradient) return gradient;
    return [color];
  };

  const previewColors: RGB[] =
    mode === "gradient"
      ? gradient
      : mode === "effect"
        ? effectPreview()
        : mode === "ambient"
          ? AMBIENT_HINT
          : mode === "battery"
            ? [batteryBandColor(batteryLevel)]
            : [color];

  return (
    <PanelSection>
      <PanelSectionRow>
        <LangToggle />
      </PanelSectionRow>
      <PanelSectionRow>
        <DeviceHeader name={device.name} color={previewColors[0]} />
      </PanelSectionRow>

      {!hasLeds && (
        <PanelSectionRow>
          <div style={{ fontSize: 13, color: "rgba(255,255,255,0.55)", padding: "4px 2px" }}>
            {t("device.noLeds")}
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
              label={mode === "ambient" ? t("device.preview.ambient") : undefined}
            />
          </PanelSectionRow>

          <PanelSectionRow>
            <ToggleField label={t("power.label")} checked={power} onChange={setPower} bottomSeparator="none" />
          </PanelSectionRow>
          <PanelSectionRow>
            <ToggleField
              label={t("chargerOnly.label")}
              description={t("chargerOnly.hint")}
              checked={chargerOnly}
              onChange={setChargerOnly}
              disabled={!power}
              bottomSeparator={caps.conflictsWithSystemRgb ? "none" : "thick"}
            />
          </PanelSectionRow>
          {caps.conflictsWithSystemRgb && (
            <PanelSectionRow>
              <ToggleField
                label={t("forceControl.label")}
                description={t("forceControl.hint")}
                checked={forceControl}
                onChange={setForceControl}
                bottomSeparator="thick"
              />
            </PanelSectionRow>
          )}

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

              {mode === "gradient" && canGradient && (
                <GradientControls
                  gradient={gradient}
                  layout={caps.layout}
                  crossfade={caps.gradientCrossfade}
                  savedGradients={savedGradients}
                  disabled={!power}
                  onChange={setGradient}
                  onSave={saveGradient}
                  onDelete={deleteGradient}
                  speed={gradientSpeed}
                  onSpeed={gradientAnimated ? setGradientSpeed : undefined}
                />
              )}

              {mode === "effect" && (
                <>
                  <PanelSectionRow>
                    <EffectsGallery
                      effects={visibleEffects}
                      selected={effect.id}
                      speed={effect.speed}
                      disabled={!power}
                      firmwareSpiral={firmwareSpiral}
                      onSelect={setEffectId}
                      onSpeed={setEffectSpeed}
                    />
                  </PanelSectionRow>
                  {effectNeed === "color" && (
                    <>
                      {canGradient && (
                        <PanelSectionRow>
                          <ToggleField
                            label={t("effect.useGradient")}
                            checked={effect.useGradient}
                            disabled={!power}
                            onChange={setEffectGradient}
                          />
                        </PanelSectionRow>
                      )}
                      <EffectSource
                        kind={canGradient && effect.useGradient ? "gradient" : "color"}
                        color={color}
                        gradient={gradient}
                      />
                    </>
                  )}
                  {effectNeed === "gradient" && (
                    <EffectSource kind="gradient" color={color} gradient={gradient} />
                  )}
                  {effectNeed === "none" && (
                    <PanelSectionRow>
                      <div
                        style={{
                          fontSize: 12,
                          color: "rgba(255,255,255,0.5)",
                          padding: "4px 2px 8px",
                        }}
                      >
                        {isFirmwareSpiral
                          ? t("effect.spiral.firmwareNote")
                          : t("effect.spectrumNote")}
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
                        {t("ambient.gameModeBanner")}
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
                      {t("ambient.stickHint")}
                    </div>
                  </PanelSectionRow>
                  <PanelSectionRow>
                    <SliderField
                      label={t("ambient.vividness")}
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
                      label={t("ambient.smoothing")}
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
                      label={t("ambient.captureRate")}
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

              {mode === "battery" && (
                <BatteryPanel
                  level={batteryLevel}
                  breathe={batteryBreathe}
                  disabled={!power}
                  onBreathe={setBatteryBreathe}
                />
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
                  label={t("brightness.label")}
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

          {caps.reconnectable && (
            <>
              <PanelSectionRow>
                <div
                  style={{ height: 1, background: "rgba(255,255,255,0.07)", margin: "14px 0 8px" }}
                />
              </PanelSectionRow>
              <PanelSectionRow>
                <ButtonItem layout="below" onClick={() => reconnect()}>
                  {t("reconnect.label")}
                </ButtonItem>
              </PanelSectionRow>
              <PanelSectionRow>
                <div
                  style={{
                    fontSize: 12,
                    color: "rgba(255,255,255,0.55)",
                    padding: "2px 2px 4px",
                    lineHeight: 1.45,
                  }}
                >
                  {t("reconnect.hint")}
                </div>
              </PanelSectionRow>
            </>
          )}
        </>
      )}

      {caps.powerLed && (
        <>
          <PanelSectionRow>
            <div
              style={{ height: 1, background: "rgba(255,255,255,0.07)", margin: "14px 0 8px" }}
            />
          </PanelSectionRow>
          <PanelSectionRow>
            <div style={{ fontWeight: 600, fontSize: 13, padding: "2px 2px 6px" }}>
              {t("powerLed.section")}
            </div>
          </PanelSectionRow>
          <PanelSectionRow>
            <ToggleField
              label={t("powerLed.label")}
              checked={powerLedOff}
              onChange={setPowerLed}
            />
          </PanelSectionRow>
          <PanelSectionRow>
            <div
              style={{
                fontSize: 12,
                color: "rgba(255,255,255,0.55)",
                padding: "0 2px 4px",
                lineHeight: 1.45,
              }}
            >
              {t("powerLed.warning")}
            </div>
          </PanelSectionRow>
        </>
      )}

      {caps.experimental.length > 0 && (
        <>
          <PanelSectionRow>
            <div
              style={{ height: 1, background: "rgba(255,255,255,0.07)", margin: "14px 0 8px" }}
            />
          </PanelSectionRow>
          <PanelSectionRow>
            <div style={{ fontWeight: 600, fontSize: 13, padding: "2px 2px 6px" }}>
              {t("experimental.title")}
            </div>
          </PanelSectionRow>
          <PanelSectionRow>
            <div
              style={{
                fontSize: 12,
                color: "rgba(255,255,255,0.55)",
                padding: "0 2px 10px",
                lineHeight: 1.45,
              }}
            >
              {t("experimental.description")}
            </div>
          </PanelSectionRow>
          {caps.experimental.map((feature) => (
            <PanelSectionRow key={feature}>
              <ToggleField
                label={t(`experimental.feature.${feature}`)}
                checked={caps.enabledExperiments.includes(feature)}
                onChange={(val) => setExperiment(feature, val)}
              />
            </PanelSectionRow>
          ))}
        </>
      )}

      {caps.conflictsWithSystemRgb && (
        <PanelSectionRow>
          <div
            style={{
              fontSize: 12,
              color: "rgba(255,255,255,0.55)",
              padding: "4px 2px 8px",
              lineHeight: 1.45,
            }}
          >
            {t("forceControl.notice")}
          </div>
        </PanelSectionRow>
      )}

      <PanelSectionRow>
        <About />
      </PanelSectionRow>
    </PanelSection>
  );
}

export default definePlugin(() => {
  // SteamOS restores its own controller lighting on resume from suspend, wiping
  // whatever the user set. Re-apply the saved settings shortly after waking so
  // the user's choice survives a sleep/wake cycle. reconnect() reconnects the
  // controller (which re-enumerates after resume) and re-applies the settings.
  // The delay lets SteamOS finish writing its color first so we win the race.
  // Registered at the plugin level so it runs even when the QAM panel is closed.
  let resumeTimer: ReturnType<typeof setTimeout> | undefined;
  const resumeReg = SteamClient?.System?.RegisterForOnResumeFromSuspend?.(() => {
    clearTimeout(resumeTimer);
    resumeTimer = setTimeout(() => {
      apiReconnect().catch(() => {});
    }, 2000);
  });
  if (!resumeReg) {
    console.warn("[Colores] SteamClient unavailable at load; resume LED restore disabled");
  }

  return {
    name: "Colores",
    titleView: <div className={staticClasses.Title}>Colores</div>,
    content: (
      <ErrorBoundary>
        <I18nProvider>
          <Content />
        </I18nProvider>
      </ErrorBoundary>
    ),
    icon: <ColorWheelIcon />,
    onDismount() {
      clearTimeout(resumeTimer);
      resumeReg?.unregister?.();
    },
  };
});
