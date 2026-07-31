import { FC, useMemo, useState } from "react";
import {
  DialogButton,
  Focusable,
  ModalRoot,
  SliderField,
  Spinner,
} from "@decky/ui";
import { hsvToRgb, rgbToCss, rgbToHsv, gradientCss } from "../color";
import {
  DEFAULT_SENSOR_BANDS,
  formatTemperature,
  sensorScaleRange,
  sensorScalePosition,
  sensorThresholdBounds,
  sensorThresholdPositions,
} from "../palette";
import { RGB, SensorBand, SensorKind } from "../types";
import { useI18n } from "../i18n";
import { FocusRoot } from "./FocusRoot";
import { Swatches } from "./Swatches";

interface Props {
  sensor: SensorKind;
  initial: SensorBand[];
  reading: number | null;
  closeModal?: () => void;
  onSave: (sensor: SensorKind, bands: SensorBand[]) => Promise<SensorBand[]>;
}

const PANEL = "rgba(255,255,255,0.045)";
const BORDER = "rgba(255,255,255,0.08)";

const cloneBands = (bands: SensorBand[]): SensorBand[] =>
  bands.map((band) => ({ ...band, color: { ...band.color } }));

const defaultBands = (sensor: SensorKind): SensorBand[] =>
  cloneBands(DEFAULT_SENSOR_BANDS[sensor]);

const selectedForReading = (reading: number | null, bands: SensorBand[]) => {
  if (reading === null) return 0;
  const index = bands.findIndex((band) => reading >= band.min);
  return index < 0 ? bands.length - 1 : index;
};

export const SensorScaleModal: FC<Props> = ({
  sensor,
  initial,
  reading,
  closeModal,
  onSave,
}) => {
  const { t, lang } = useI18n();
  const [bands, setBands] = useState(() => cloneBands(initial));
  const [selected, setSelected] = useState(() => selectedForReading(reading, initial));
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState(false);
  const current = bands[selected];
  const hsv = useMemo(() => rgbToHsv(current.color), [current.color]);
  const range = sensorScaleRange(sensor, bands);
  const bounds = sensorThresholdBounds(sensor, selected, bands);
  const thresholdPositions = sensorThresholdPositions(sensor, bands);
  const marker = reading === null ? null : sensorScalePosition(sensor, bands, reading);
  const unit = sensor === "battery" ? "%" : " °C";
  const formattedReading =
    reading === null
      ? t("temperature.noReading")
      : sensor === "temperature"
        ? `${formatTemperature(reading, lang)}${unit}`
        : `${Math.round(reading)}${unit}`;
  const names = Array.from({ length: 5 }, (_, index) =>
    t(`${sensor}.band.${index + 1}`),
  );

  const replaceBand = (index: number, patch: Partial<SensorBand>) =>
    setBands((value) =>
      value.map((band, bandIndex) =>
        bandIndex === index ? { ...band, ...patch } : band,
      ),
    );

  const replaceColor = (color: RGB) => replaceBand(selected, { color });
  const editHue = (h: number) =>
    replaceColor(hsvToRgb(h, hsv.s, Math.max(hsv.v, 60)));
  const editSaturation = (s: number) =>
    replaceColor(hsvToRgb(hsv.h, s, Math.max(hsv.v, 60)));

  const save = async () => {
    setSaving(true);
    setSaveError(false);
    try {
      await onSave(sensor, bands);
      closeModal?.();
    } catch (error) {
      console.error("Colores: sensor scale save failed", error);
      setSaveError(true);
      setSaving(false);
    }
  };

  return (
    <ModalRoot
      closeModal={closeModal}
      onCancel={saving ? undefined : closeModal}
      onEscKeypress={saving ? undefined : closeModal}
      bAllowFullSize
      bDisableBackgroundDismiss={saving}
    >
      <FocusRoot>
        <Focusable
          style={{ display: "flex", flexDirection: "column", gap: 14, padding: 4 }}
        >
          <div style={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", gap: 16 }}>
            <div>
              <div style={{ fontSize: 10, color: "rgba(255,255,255,0.45)", textTransform: "uppercase", letterSpacing: "0.12em" }}>
                {t(`sensors.${sensor}`)}
              </div>
              <div style={{ fontSize: 19, fontWeight: 800, marginTop: 3 }}>
                {t("sensorScale.title")}
              </div>
              <div style={{ fontSize: 11.5, color: "rgba(255,255,255,0.5)", marginTop: 3 }}>
                {t("sensorScale.subtitle")}
              </div>
            </div>
            <DialogButton
              disabled={saving}
              onClick={() => setBands(defaultBands(sensor))}
              style={{ width: "auto", minWidth: 108, background: "rgba(255,255,255,0.06)", border: `1px solid ${BORDER}` }}
            >
              {t("sensorScale.reset")}
            </DialogButton>
          </div>

          <div style={{ padding: 15, borderRadius: 16, background: PANEL, border: `1px solid ${BORDER}` }}>
            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 10, fontSize: 10.5, color: "rgba(255,255,255,0.48)" }}>
              <span>{t("sensorScale.preview")}</span>
              <strong style={{ color: "rgba(255,255,255,0.86)", fontSize: 12 }}>
                {formattedReading}
              </strong>
            </div>
            <div style={{ position: "relative", height: 20, borderRadius: 10, background: gradientCss([...bands].reverse().map((band) => band.color)), boxShadow: "inset 0 0 0 1px rgba(255,255,255,0.14), 0 7px 22px rgba(0,0,0,0.28)" }}>
              {thresholdPositions.map((position, index) => (
                <div
                  key={index}
                  aria-hidden
                  style={{ position: "absolute", top: 2, bottom: 2, left: `calc(${position}% - 1px)`, width: 2, borderRadius: 2, background: "rgba(0,0,0,0.5)", boxShadow: "0 0 0 1px rgba(255,255,255,0.3)" }}
                />
              ))}
              {marker !== null && (
                <div style={{ position: "absolute", top: -4, bottom: -4, left: `calc(${marker}% - 2px)`, width: 4, borderRadius: 4, background: "#fff", boxShadow: "0 1px 6px rgba(0,0,0,0.8)" }} />
              )}
            </div>
            <div style={{ display: "flex", justifyContent: "space-between", marginTop: 7, fontSize: 10, color: "rgba(255,255,255,0.38)" }}>
              <span>{range.min}{unit}</span>
              <span>{range.max}{unit}</span>
            </div>
          </div>

          <Focusable role="radiogroup" aria-label={t("sensorScale.bandGroup")} style={{ display: "grid", gridTemplateColumns: "repeat(5, minmax(0, 1fr))", gap: 7 }}>
            {bands.map((band, index) => {
              const active = index === selected;
              return (
                <Focusable
                  key={index}
                  role="radio"
                  aria-checked={active}
                  aria-label={names[index]}
                  onActivate={() => setSelected(index)}
                  onClick={() => setSelected(index)}
                  style={{ minWidth: 0, padding: "9px 5px", borderRadius: 11, textAlign: "center", background: active ? "rgba(var(--colores-accent-rgb), 0.18)" : PANEL, border: active ? "1px solid rgb(var(--colores-accent-rgb))" : `1px solid ${BORDER}`, cursor: "pointer" }}
                >
                  <div style={{ width: 22, height: 22, margin: "0 auto 6px", borderRadius: 7, background: rgbToCss(band.color), boxShadow: `0 0 12px ${rgbToCss(band.color)}, inset 0 0 0 1px rgba(255,255,255,0.2)` }} />
                  <div style={{ overflow: "hidden", textOverflow: "ellipsis", fontSize: 9.5, fontWeight: 700, color: active ? "#fff" : "rgba(255,255,255,0.58)" }}>
                    {names[index]}
                  </div>
                </Focusable>
              );
            })}
          </Focusable>

          <div style={{ padding: "13px 16px 15px", borderRadius: 16, background: PANEL, border: `1px solid ${BORDER}` }}>
            <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 8 }}>
              <div style={{ width: 30, height: 30, borderRadius: 9, background: rgbToCss(current.color), boxShadow: `0 0 13px ${rgbToCss(current.color)}, inset 0 0 0 1px rgba(255,255,255,0.2)` }} />
              <div>
                <div style={{ fontSize: 12.5, fontWeight: 700 }}>{names[selected]}</div>
                <div style={{ fontSize: 10, color: "rgba(255,255,255,0.43)", marginTop: 2 }}>
                  {selected === bands.length - 1
                    ? t("sensorScale.minimumBand")
                    : t("sensorScale.startsAt", { n: current.min, unit })}
                </div>
              </div>
            </div>

            {bounds ? (
              <SliderField
                label={t("sensorScale.threshold")}
                value={current.min}
                min={bounds.min}
                max={bounds.max}
                step={1}
                showValue
                valueSuffix={unit}
                disabled={saving}
                onChange={(min) => replaceBand(selected, { min })}
              />
            ) : (
              <div style={{ padding: "8px 0 10px", fontSize: 10.5, color: "rgba(255,255,255,0.42)" }}>
                {t("sensorScale.minimumHint")}
              </div>
            )}

            <div style={{ margin: "3px 0 7px", fontSize: 10, color: "rgba(255,255,255,0.42)", textTransform: "uppercase", letterSpacing: "0.08em" }}>
              {t("sensorScale.color")}
            </div>
            <Swatches selected={current.color} disabled={saving} onPick={replaceColor} />
            <SliderField label={t("color.hue")} value={hsv.h} min={0} max={360} step={1} disabled={saving} onChange={editHue} />
            <SliderField label={t("color.saturation")} value={hsv.s} min={0} max={100} step={1} showValue valueSuffix="%" disabled={saving} onChange={editSaturation} />
          </div>

          {saveError && (
            <div style={{ padding: "9px 11px", borderRadius: 10, background: "rgba(255,70,70,0.1)", color: "#ff9b9b", fontSize: 11 }}>
              {t("sensorScale.saveError")}
            </div>
          )}

          <Focusable style={{ display: "flex", gap: 10 }}>
            <DialogButton disabled={saving} onClick={() => closeModal?.()} style={{ flex: 1, background: "rgba(255,255,255,0.06)", border: `1px solid ${BORDER}` }}>
              {t("sensorScale.cancel")}
            </DialogButton>
            <DialogButton disabled={saving} onClick={save} style={{ flex: 1, background: "linear-gradient(135deg, rgb(var(--colores-accent-rgb)), #00bde8)", border: "none" }}>
              {saving ? <Spinner width={18} height={18} /> : t("sensorScale.save")}
            </DialogButton>
          </Focusable>
        </Focusable>
      </FocusRoot>
    </ModalRoot>
  );
};
