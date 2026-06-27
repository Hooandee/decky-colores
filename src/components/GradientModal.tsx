import { FC, useState } from "react";
import {
  ModalRoot,
  Focusable,
  SliderField,
  ButtonItem,
  DialogButton,
} from "@decky/ui";
import { RGB, GradientPreset, ZoneGroup } from "../types";
import { hsvToRgb, rgbToHsv, rgbToCss, gradientCss, expandGradient } from "../color";
import { GRADIENT_PRESETS, harmoniousGradient, randomGradient } from "../palette";
import { useI18n } from "../i18n";

interface GradientModalProps {
  initial: RGB[];
  layout: ZoneGroup[];
  closeModal?: () => void;
  onApply: (stops: RGB[]) => void;
}

const ACCENT = "#7c5cff";
const PANEL_BG = "rgba(18, 18, 24, 0.6)";
const BORDER = "rgba(255,255,255,0.08)";

const glowFor = (stops: RGB[]): string => {
  const a = stops[0];
  const b = stops[stops.length - 1];
  return `0 12px 40px -8px ${rgbToCss(a)}, 0 12px 40px -8px ${rgbToCss(b)}`;
};

const SectionLabel: FC<{ children: string }> = ({ children }) => (
  <div
    style={{
      fontSize: 11,
      fontWeight: 700,
      letterSpacing: "0.14em",
      textTransform: "uppercase",
      color: "rgba(255,255,255,0.5)",
      marginBottom: 10,
    }}
  >
    {children}
  </div>
);

const Card: FC<{ children: React.ReactNode; style?: React.CSSProperties }> = ({ children, style }) => (
  <div
    style={{
      background: PANEL_BG,
      border: `1px solid ${BORDER}`,
      borderRadius: 16,
      padding: 16,
      ...style,
    }}
  >
    {children}
  </div>
);

const StopEditor: FC<{
  label: string;
  color: RGB;
  hueLabel: string;
  satLabel: string;
  onChange: (color: RGB) => void;
}> = ({ label, color, hueLabel, satLabel, onChange }) => {
  const hsv = rgbToHsv(color);
  const setHue = (h: number) => onChange(hsvToRgb(h, hsv.s, Math.max(hsv.v, 60)));
  const setSat = (s: number) => onChange(hsvToRgb(hsv.h, s, Math.max(hsv.v, 60)));

  return (
    <Focusable
      style={{
        display: "flex",
        flexDirection: "column",
        gap: 4,
        padding: 12,
        borderRadius: 14,
        background: "rgba(255,255,255,0.03)",
        border: `1px solid ${BORDER}`,
      }}
    >
      <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
        <div
          style={{
            width: 32,
            height: 32,
            borderRadius: 9,
            flex: "0 0 auto",
            background: rgbToCss(color),
            boxShadow: `0 0 12px ${rgbToCss(color)}, inset 0 0 0 1px rgba(255,255,255,0.18)`,
          }}
        />
        <div style={{ fontSize: 13, fontWeight: 600, color: "rgba(255,255,255,0.85)" }}>{label}</div>
      </div>
      <SliderField label={hueLabel} value={hsv.h} min={0} max={360} step={1} onChange={setHue} />
      <SliderField label={satLabel} value={hsv.s} min={0} max={100} step={1} onChange={setSat} />
    </Focusable>
  );
};

const StickGroup: FC<{
  title: string;
  indices: number[];
  stops: RGB[];
  hueLabel: string;
  satLabel: string;
  colorLabel: (i: number, total: number) => string;
  onChange: (index: number, color: RGB) => void;
}> = ({ title, indices, stops, hueLabel, satLabel, colorLabel, onChange }) => (
  <div>
    <SectionLabel>{title}</SectionLabel>
    <Focusable style={{ display: "flex", flexDirection: "column", gap: 10 }}>
      {indices.map((zone, i) => (
        <StopEditor
          key={zone}
          label={colorLabel(i, indices.length)}
          hueLabel={hueLabel}
          satLabel={satLabel}
          color={stops[zone] ?? { r: 255, g: 255, b: 255 }}
          onChange={(c) => onChange(zone, c)}
        />
      ))}
    </Focusable>
  </div>
);

export const GradientModal: FC<GradientModalProps> = ({ initial, layout, closeModal, onApply }) => {
  const { t } = useI18n();
  const groups =
    layout.length > 0 ? layout : [{ name: t("gradient.defaultGroup"), region: [], zones: [0, 1] }];
  const count = Math.max(2, groups.reduce((n, g) => n + g.zones.length, 0));
  const [stops, setStops] = useState<RGB[]>(() => expandGradient(initial, count));
  const hueLabel = t("color.hue");
  const satLabel = t("color.saturation");
  const colorLabel = (i: number, total: number) =>
    total > 1 ? t("gradient.colorN").replace("{n}", String(i + 1)) : t("gradient.color");
  const presetName = (preset: GradientPreset) =>
    t(`gradient.preset.${preset.name.toLowerCase()}`);

  const setStopAt = (index: number, color: RGB) =>
    setStops((prev) => prev.map((c, i) => (i === index ? color : c)));

  const apply = () => {
    onApply(stops);
    closeModal?.();
  };

  return (
    <ModalRoot closeModal={closeModal} onCancel={closeModal} onOK={apply}>
      <Focusable style={{ display: "flex", flexDirection: "column", gap: 20, padding: 4 }}>
        <div>
          <div style={{ fontSize: 18, fontWeight: 800, color: "#fff" }}>{t("gradient.title")}</div>
          <div style={{ fontSize: 12, color: "rgba(255,255,255,0.5)", marginTop: 2 }}>
            {t("gradient.subtitle")}
          </div>
        </div>

        <div
          style={{
            height: 84,
            borderRadius: 18,
            background: gradientCss(stops),
            boxShadow: `${glowFor(stops)}, inset 0 0 0 1px rgba(255,255,255,0.12)`,
            transition: "background 160ms ease, box-shadow 200ms ease",
          }}
        />

        <Card>
          <SectionLabel>{t("gradient.presets")}</SectionLabel>
          <Focusable
            style={{
              display: "grid",
              gridTemplateColumns: "repeat(2, 1fr)",
              gap: 10,
              maxHeight: 168,
              overflowY: "auto",
              paddingRight: 2,
            }}
          >
            {GRADIENT_PRESETS.map((preset: GradientPreset, i: number) => (
              <Focusable
                key={i}
                onActivate={() => setStops(expandGradient(preset.stops, count))}
                onClick={() => setStops(expandGradient(preset.stops, count))}
                style={{ display: "flex", flexDirection: "column", gap: 6, cursor: "pointer" }}
              >
                <div
                  style={{
                    height: 40,
                    borderRadius: 12,
                    background: gradientCss(preset.stops),
                    boxShadow: "inset 0 0 0 1px rgba(255,255,255,0.12)",
                  }}
                />
                <div
                  style={{
                    fontSize: 11,
                    fontWeight: 600,
                    color: "rgba(255,255,255,0.7)",
                    textAlign: "center",
                  }}
                >
                  {presetName(preset)}
                </div>
              </Focusable>
            ))}
          </Focusable>
        </Card>

        <Card>
          <Focusable style={{ display: "flex", flexDirection: "column", gap: 16 }}>
            {groups.map((group, i) => (
              <StickGroup
                key={i}
                title={group.name}
                indices={group.zones}
                stops={stops}
                hueLabel={hueLabel}
                satLabel={satLabel}
                colorLabel={colorLabel}
                onChange={setStopAt}
              />
            ))}
          </Focusable>
        </Card>

        <Card>
          <SectionLabel>{t("gradient.wizard")}</SectionLabel>
          <Focusable style={{ display: "flex", gap: 10 }}>
            <DialogButton
              onClick={() => setStops(expandGradient(randomGradient(), count))}
              style={{ flex: 1, background: `linear-gradient(135deg, ${ACCENT}, #00c4ff)`, border: "none" }}
            >
              {t("gradient.surprise")}
            </DialogButton>
            <DialogButton
              onClick={() => setStops(expandGradient(harmoniousGradient(stops[0]), count))}
              style={{ flex: 1, background: "rgba(255,255,255,0.06)", border: `1px solid ${BORDER}` }}
            >
              {t("gradient.autoPalette")}
            </DialogButton>
          </Focusable>
        </Card>

        <Focusable style={{ display: "flex", flexDirection: "column", gap: 4 }}>
          <ButtonItem layout="below" bottomSeparator="none" onClick={apply}>
            {t("gradient.apply")}
          </ButtonItem>
          <ButtonItem layout="below" bottomSeparator="none" onClick={() => closeModal?.()}>
            {t("gradient.cancel")}
          </ButtonItem>
        </Focusable>
      </Focusable>
    </ModalRoot>
  );
};
