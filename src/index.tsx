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
import { useCallback, useEffect, useMemo, useState } from "react";

import { useColores } from "./useColores";
import { getAmbilightStatus, getAudioStatus, getTemperature, getPerformance, reconnect as apiReconnect } from "./api";
import { rgbToCss, gradientCss } from "./color";
import { Mode, RGB, ZoneGroup, GradientPreset, EffectColorNeed, Capabilities } from "./types";
import { DevicePreview } from "./components/DevicePreview";
import { ColorEditor } from "./components/ColorEditor";
import { EffectsGallery } from "./components/EffectsGallery";
import { GradientModal } from "./components/GradientModal";
import { TabBar } from "./components/TabBar";
import { SettingsSection } from "./components/SettingsSection";
import { Divider } from "./components/Divider";
import { ColorWheelIcon } from "./components/ColorWheelIcon";
import {
  GRADIENT_PRESETS,
  EFFECT_PRESETS,
  BATTERY_BANDS,
  batteryBandColor,
  TEMPERATURE_BANDS,
  TEMPERATURE_RANGE,
  temperatureBandColor,
  PERFORMANCE_STOPS,
  performanceMeterColors,
  audioVuColors,
  clockColor,
} from "./palette";
import { Tabs } from "./components/Tabs";
import { I18nProvider, useI18n } from "./i18n";
import { useUpdate } from "./updater/useUpdate";
import { AlertDot } from "./updater/AlertDot";
import { useLayout } from "./nav/store";
import { visibleIds } from "./nav/layout";
import { PINNED_TAB, tabMeta, TAB_META, SENSOR_TAB, SENSOR_MODES, tabForMode } from "./nav/manifest";
import { useShoulderNav } from "./nav/useShoulderNav";
import { readActiveTab, writeActiveTab, readSensorMode, writeSensorMode } from "./nav/activeTab";

function DeviceHeader({ name, color }: { name: string; color: RGB }) {
  const css = rgbToCss(color);
  const tint = `rgba(${color.r}, ${color.g}, ${color.b}, 0.55)`;
  return (
    <div
      style={{
        display: "flex",
        alignItems: "center",
        gap: 10,
        padding: "7px 12px",
        marginBottom: 10,
        borderRadius: 10,
        background: "rgba(255,255,255,0.04)",
        border: `1.5px solid ${tint}`,
        boxShadow: "inset 0 0 0 1px rgba(255,255,255,0.05)",
      }}
    >
      <div
        style={{
          width: 12,
          height: 12,
          borderRadius: "50%",
          flexShrink: 0,
          background: css,
          boxShadow: `0 0 8px ${css}`,
        }}
      />
      <div
        style={{
          fontWeight: 600,
          fontSize: 14,
          minWidth: 0,
          whiteSpace: "nowrap",
          overflow: "hidden",
          textOverflow: "ellipsis",
        }}
      >
        {name}
      </div>
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

function SensorMeter({
  hint,
  barStops,
  markerPct,
  leftLabel,
  centerLabel,
  rightLabel,
  breatheLabel,
  breatheHint,
  breathe,
  disabled,
  onBreathe,
}: {
  hint: string;
  barStops: RGB[];
  markerPct: number | null;
  leftLabel: string;
  centerLabel: string;
  rightLabel: string;
  breatheLabel?: string;
  breatheHint?: string;
  breathe?: boolean;
  disabled?: boolean;
  onBreathe?: (on: boolean) => void;
}) {
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
          {hint}
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
            {markerPct !== null && (
              <div
                style={{
                  position: "absolute",
                  top: -3,
                  bottom: -3,
                  left: `calc(${markerPct}% - 2px)`,
                  width: 4,
                  borderRadius: 2,
                  background: "#fff",
                  boxShadow: "0 0 4px rgba(0,0,0,0.6)",
                }}
              />
            )}
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
            <span>{leftLabel}</span>
            <span style={{ color: "rgba(255,255,255,0.8)", fontWeight: 600 }}>{centerLabel}</span>
            <span>{rightLabel}</span>
          </div>
        </div>
      </PanelSectionRow>
      {onBreathe && (
        <PanelSectionRow>
          <ToggleField
            label={breatheLabel ?? ""}
            description={breatheHint}
            checked={!!breathe}
            disabled={disabled}
            onChange={onBreathe}
            bottomSeparator="none"
          />
        </PanelSectionRow>
      )}
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
  const marker = Math.max(0, Math.min(100, level));
  return (
    <SensorMeter
      hint={t("battery.hint")}
      barStops={[...BATTERY_BANDS].reverse().map((b) => b.color)}
      markerPct={marker}
      leftLabel="0%"
      centerLabel={t("battery.level", { n: marker })}
      rightLabel="100%"
      breatheLabel={t("battery.breathe.label")}
      breatheHint={t("battery.breathe.hint")}
      breathe={breathe}
      disabled={disabled}
      onBreathe={onBreathe}
    />
  );
}

function TemperaturePanel({
  temp,
  breathe,
  disabled,
  onBreathe,
}: {
  temp: number | null;
  breathe: boolean;
  disabled?: boolean;
  onBreathe: (on: boolean) => void;
}) {
  const { t } = useI18n();
  const { min, max } = TEMPERATURE_RANGE;
  const markerPct =
    temp === null ? null : Math.max(0, Math.min(100, ((temp - min) / (max - min)) * 100));
  return (
    <SensorMeter
      hint={t("temperature.hint")}
      barStops={[...TEMPERATURE_BANDS].reverse().map((b) => b.color)}
      markerPct={markerPct}
      leftLabel={`${min}°`}
      centerLabel={
        temp === null ? t("temperature.noReading") : t("temperature.reading", { n: Math.round(temp) })
      }
      rightLabel={`${max}°`}
      breatheLabel={t("temperature.breathe.label")}
      breatheHint={t("temperature.breathe.hint")}
      breathe={breathe}
      disabled={disabled}
      onBreathe={onBreathe}
    />
  );
}

function PerformancePanel({ load, disabled }: { load: number | null; disabled?: boolean }) {
  const { t } = useI18n();
  return (
    <SensorMeter
      hint={t("performance.hint")}
      barStops={PERFORMANCE_STOPS}
      markerPct={load}
      leftLabel="0%"
      centerLabel={load === null ? t("performance.noReading") : t("performance.reading", { n: Math.round(load) })}
      rightLabel="100%"
      disabled={disabled}
    />
  );
}

function SensorsPanel({
  mode,
  availableModes,
  level,
  temp,
  load,
  batteryBreathe,
  temperatureBreathe,
  disabled,
  onSelectMode,
  onBatteryBreathe,
  onTemperatureBreathe,
}: {
  mode: Mode;
  availableModes: Mode[];
  level: number;
  temp: number | null;
  load: number | null;
  batteryBreathe: boolean;
  temperatureBreathe: boolean;
  disabled?: boolean;
  onSelectMode: (m: Mode) => void;
  onBatteryBreathe: (on: boolean) => void;
  onTemperatureBreathe: (on: boolean) => void;
}) {
  const { t } = useI18n();
  return (
    <>
      {availableModes.length > 1 && (
        <PanelSectionRow>
          <div style={{ padding: "2px 0 6px" }}>
            <Tabs<Mode>
              value={mode}
              tabs={availableModes}
              onChange={onSelectMode}
              label={(m) => t(`sensors.${m}` as "sensors.battery" | "sensors.temperature" | "sensors.performance")}
            />
          </div>
        </PanelSectionRow>
      )}
      {mode === "performance" ? (
        <PerformancePanel load={load} disabled={disabled} />
      ) : mode === "temperature" ? (
        <TemperaturePanel
          temp={temp}
          breathe={temperatureBreathe}
          disabled={disabled}
          onBreathe={onTemperatureBreathe}
        />
      ) : (
        <BatteryPanel
          level={level}
          breathe={batteryBreathe}
          disabled={disabled}
          onBreathe={onBatteryBreathe}
        />
      )}
    </>
  );
}

function modeIdsFor(caps: Capabilities | null): Mode[] {
  if (!caps || !caps.color) return [];
  const modes: Mode[] = ["solid"];
  if (caps.zones >= 1) modes.push("gradient");
  modes.push("effect");
  if (caps.batteryMode) modes.push("battery");
  if (caps.temperatureMode) modes.push("temperature");
  if (caps.performanceMode) modes.push("performance");
  if (caps.clockMode) modes.push("clock");
  if (caps.audioMode) modes.push("vu");
  if (caps.ambilight) modes.push("ambient");
  return modes;
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
    setAmbilightSampling,
    saveGradient,
    deleteGradient,
    setExperiment,
    setPowerLed,
    setForceControl,
    setIndicator,
    setRememberStartup,
    setBatteryBreathe,
    setTemperatureBreathe,
    reconnect,
  } = useColores();
  const { t, lang } = useI18n();
  const { hasUpdate } = useUpdate(lang);
  const [ambStatus, setAmbStatus] = useState<string>("idle");
  const [audStatus, setAudStatus] = useState<string>("idle");
  const [tempReading, setTempReading] = useState<number | null>(null);
  const [perfReading, setPerfReading] = useState<number | null>(null);
  const [viewingSettings, setViewingSettings] = useState<boolean>(
    () => readActiveTab() === PINNED_TAB,
  );
  const layout = useLayout();

  const caps = state?.capabilities ?? null;
  const modeIds = modeIdsFor(caps);
  const availableTabSet = new Set(modeIds.map((m) => tabForMode(m)));
  const availableTabIds = [
    ...TAB_META.filter((m) => m.id !== PINNED_TAB && availableTabSet.has(m.id)).map((m) => m.id),
    PINNED_TAB,
  ];
  const visibleTabIds = visibleIds(availableTabIds, layout.tabs, [PINNED_TAB]);
  const visibleModeCount = visibleTabIds.filter((id) => id !== PINNED_TAB).length;
  const availableSensorModes = SENSOR_MODES.filter((m) => modeIds.includes(m)) as Mode[];
  const desiredTab = viewingSettings || !state ? PINNED_TAB : tabForMode(state.mode);
  const activeTab = visibleTabIds.includes(desiredTab) ? desiredTab : PINNED_TAB;
  const currentSensorMode: Mode | null =
    state && availableSensorModes.includes(state.mode)
      ? state.mode
      : (availableSensorModes[0] ?? null);
  const contentMode: Mode | null =
    activeTab === PINNED_TAB
      ? null
      : activeTab === SENSOR_TAB
        ? currentSensorMode
        : (activeTab as Mode);
  const highlight = activeTab;

  const selectSensorMode = useCallback(
    (m: Mode) => {
      writeSensorMode(m);
      setMode(m);
    },
    [setMode],
  );

  const select = useCallback(
    (id: string) => {
      if (id === PINNED_TAB) {
        setViewingSettings(true);
        writeActiveTab(PINNED_TAB);
      } else {
        setViewingSettings(false);
        writeActiveTab(id);
        if (id === SENSOR_TAB) {
          const last = readSensorMode();
          const target =
            last && availableSensorModes.includes(last as Mode)
              ? (last as Mode)
              : availableSensorModes[0];
          if (target) setMode(target);
        } else {
          setMode(id as Mode);
        }
      }
    },
    [setMode, availableSensorModes],
  );

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

  const temperatureActive = state?.mode === "temperature";
  useEffect(() => {
    if (!temperatureActive) return;
    let alive = true;
    const poll = () =>
      getTemperature()
        .then((v) => alive && setTempReading(v))
        .catch(() => {});
    poll();
    const timer = setInterval(poll, 2000);
    return () => {
      alive = false;
      clearInterval(timer);
    };
  }, [temperatureActive]);

  const audioActive = state?.mode === "vu" && state?.power;
  useEffect(() => {
    if (!audioActive) return;
    let alive = true;
    const poll = () =>
      getAudioStatus()
        .then((v) => alive && setAudStatus(v))
        .catch(() => {});
    poll();
    const timer = setInterval(poll, 2000);
    return () => {
      alive = false;
      clearInterval(timer);
    };
  }, [audioActive]);

  const performanceActive = state?.mode === "performance" && state?.power;
  useEffect(() => {
    if (!performanceActive) return;
    let alive = true;
    const poll = () =>
      getPerformance()
        .then((v) => alive && setPerfReading(v))
        .catch(() => {});
    poll();
    const timer = setInterval(poll, 1500);
    return () => {
      alive = false;
      clearInterval(timer);
    };
  }, [performanceActive]);

  useEffect(() => {
    if (!state?.capabilities.conflictsWithSystemRgb || !state?.forceControl) return;
    apiReconnect().catch(() => {});
  }, [state?.capabilities.conflictsWithSystemRgb, state?.forceControl]);

  useShoulderNav(visibleTabIds, highlight, select);

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
    capabilities,
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
    temperatureBreathe,
    indicatorOn,
    indicatorLevel,
    rememberStartup,
  } = state;
  const currentTemp = tempReading ?? state.temperature;
  const hasLeds = capabilities.color || capabilities.brightness;
  const showDeviceControls = hasLeds && (contentMode !== null || visibleModeCount === 0);

  const canGradient = capabilities.color && capabilities.zones >= 1;
  const gradientAnimated = canGradient && !capabilities.perZone;

  const visibleEffects = capabilities.supportedEffects.length > 0
    ? EFFECT_PRESETS.filter((e) => capabilities.supportedEffects.includes(e.id))
    : EFFECT_PRESETS;

  const selectedEffect = visibleEffects.find((e) => e.id === effect.id) ?? visibleEffects[0];

  const firmwareSpiral = capabilities.hardwareEffects;
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

  const previewColorsFor = (): RGB[] => {
    switch (mode) {
      case "gradient":
        return gradient;
      case "effect":
        return effectPreview();
      case "ambient":
        return AMBIENT_HINT;
      case "battery":
        return [batteryBandColor(batteryLevel)];
      case "temperature":
        return [temperatureBandColor(currentTemp ?? TEMPERATURE_RANGE.min)];
      case "performance":
        return performanceMeterColors(perfReading ?? 0, capabilities.zones);
      case "clock": {
        const now = new Date();
        return [clockColor(now.getHours() + now.getMinutes() / 60)];
      }
      case "vu":
        return audioVuColors(0.6, capabilities.zones);
      default:
        return [color];
    }
  };
  const previewColors: RGB[] = previewColorsFor();

  const tabItems = visibleTabIds.map((id) => {
    const meta = tabMeta(id);
    return {
      id,
      icon: meta?.icon,
      label: meta ? t(meta.labelKey) : id,
      badge: id === PINNED_TAB ? <AlertDot show={hasUpdate} /> : undefined,
    };
  });

  const renderModeContent = () => {
    switch (contentMode) {
      case "solid":
        return <ColorEditor color={color} disabled={!power} onChange={setColor} />;
      case "gradient":
        return canGradient ? (
          <GradientControls
            gradient={gradient}
            layout={capabilities.layout}
            crossfade={capabilities.gradientCrossfade}
            savedGradients={savedGradients}
            disabled={!power}
            onChange={setGradient}
            onSave={saveGradient}
            onDelete={deleteGradient}
            speed={gradientSpeed}
            onSpeed={gradientAnimated ? setGradientSpeed : undefined}
          />
        ) : null;
      case "effect":
        return (
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
                <div style={{ fontSize: 12, color: "rgba(255,255,255,0.5)", padding: "4px 2px 8px" }}>
                  {isFirmwareSpiral ? t("effect.spiral.firmwareNote") : t("effect.spectrumNote")}
                </div>
              </PanelSectionRow>
            )}
          </>
        );
      case "ambient":
        return (
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
              <div style={{ fontSize: 12, color: "rgba(255,255,255,0.55)", padding: "4px 2px 12px", lineHeight: 1.45 }}>
                {t("ambient.stickHint")}
              </div>
            </PanelSectionRow>
            {capabilities.layoutKind === "bar" && (
              <PanelSectionRow>
                <div style={{ padding: "2px 0 10px" }}>
                  <Tabs<string>
                    value={ambilight.sampling}
                    tabs={["columns", "bottom_edge"]}
                    onChange={setAmbilightSampling}
                    label={(m) =>
                      t(`ambient.sampling.${m}` as "ambient.sampling.columns" | "ambient.sampling.bottom_edge")
                    }
                  />
                </div>
              </PanelSectionRow>
            )}
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
                bottomSeparator="none"
              />
            </PanelSectionRow>
          </>
        );
      case "battery":
      case "temperature":
      case "performance":
        return (
          <SensorsPanel
            mode={contentMode}
            availableModes={availableSensorModes}
            level={batteryLevel}
            temp={currentTemp}
            load={perfReading}
            batteryBreathe={batteryBreathe}
            temperatureBreathe={temperatureBreathe}
            disabled={!power}
            onSelectMode={selectSensorMode}
            onBatteryBreathe={setBatteryBreathe}
            onTemperatureBreathe={setTemperatureBreathe}
          />
        );
      case "clock":
        return (
          <PanelSectionRow>
            <div style={{ fontSize: 12, color: "rgba(255,255,255,0.55)", padding: "4px 2px 8px", lineHeight: 1.45 }}>
              {t("clock.hint")}
            </div>
          </PanelSectionRow>
        );
      case "vu":
        return (
          <>
            {power && audStatus === "no_source" && (
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
                  {t("vu.noAudio")}
                </div>
              </PanelSectionRow>
            )}
            <PanelSectionRow>
              <div style={{ fontSize: 12, color: "rgba(255,255,255,0.55)", padding: "4px 2px 8px", lineHeight: 1.45 }}>
                {t("vu.hint")}
              </div>
            </PanelSectionRow>
          </>
        );
      default:
        return null;
    }
  };

  return (
    <PanelSection>
      <PanelSectionRow>
        <DeviceHeader name={device.name} color={previewColors[0] ?? color} />
      </PanelSectionRow>
      <PanelSectionRow>
        <TabBar tabs={tabItems} activeId={highlight} onSelect={select} />
      </PanelSectionRow>

      {contentMode && (
        <PanelSectionRow>
          <div style={{ marginTop: 12 }}>
            <DevicePreview
              colors={previewColors}
              brightness={brightness}
              power={power}
              layoutKind={capabilities.layoutKind}
              segments={capabilities.zones}
              label={contentMode === "ambient" ? t("device.preview.ambient") : undefined}
            />
          </div>
        </PanelSectionRow>
      )}

      {showDeviceControls && (
        <>
          <PanelSectionRow>
            <ToggleField label={t("power.label")} checked={power} onChange={setPower} bottomSeparator="none" />
          </PanelSectionRow>
          {capabilities.hasBattery && (
            <PanelSectionRow>
              <ToggleField
                label={t("chargerOnly.label")}
                description={t("chargerOnly.hint")}
                checked={chargerOnly}
                onChange={setChargerOnly}
                disabled={!power}
                bottomSeparator="none"
              />
            </PanelSectionRow>
          )}
          {capabilities.indicatorLed && (
            <>
              <PanelSectionRow>
                <ToggleField
                  label={t("indicator.label")}
                  description={t("indicator.hint")}
                  checked={indicatorOn}
                  onChange={(v) => setIndicator(v, indicatorLevel)}
                  bottomSeparator="none"
                />
              </PanelSectionRow>
              {indicatorOn && (
                <PanelSectionRow>
                  <SliderField
                    label={t("indicator.brightness")}
                    value={indicatorLevel}
                    min={0}
                    max={100}
                    step={5}
                    valueSuffix="%"
                    showValue
                    onChange={(v) => setIndicator(indicatorOn, v)}
                    bottomSeparator="none"
                  />
                </PanelSectionRow>
              )}
            </>
          )}
          {capabilities.persistentStartup && (
            <PanelSectionRow>
              <ToggleField
                label={t("startup.remember")}
                description={t("startup.remember.hint")}
                checked={rememberStartup}
                onChange={setRememberStartup}
                bottomSeparator="none"
              />
            </PanelSectionRow>
          )}
        </>
      )}

      {showDeviceControls && contentMode && <Divider margin="18px 0 12px" />}

      {contentMode && renderModeContent()}

      {showDeviceControls && capabilities.brightness && (
        <>
          <Divider margin="18px 0 12px" />
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

      {!contentMode && (
        <>
          {!hasLeds && (
            <PanelSectionRow>
              <div style={{ fontSize: 13, color: "rgba(255,255,255,0.55)", padding: "8px 2px 4px" }}>
                {t("device.noLeds")}
              </div>
            </PanelSectionRow>
          )}
          <SettingsSection
            caps={capabilities}
            availableTabIds={availableTabIds}
            lang={lang}
            forceControl={forceControl}
            powerLedOff={powerLedOff}
            onForceControl={setForceControl}
            onPowerLed={setPowerLed}
            onExperiment={setExperiment}
            onReconnect={reconnect}
          />
        </>
      )}
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
