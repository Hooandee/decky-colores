import { FC, useState } from "react";
import {
  ModalRoot,
  Focusable,
  SliderField,
  DialogButton,
  TextField,
} from "@decky/ui";
import { RGB, GradientPreset, ZoneGroup } from "../types";
import { hsvToRgb, rgbToHsv, rgbToCss, gradientCss, expandGradient } from "../color";
import { GRADIENT_PRESETS, harmoniousGradient, randomGradient, suggestGradientName } from "../palette";
import { useI18n } from "../i18n";
import { Tabs } from "./Tabs";

interface GradientModalProps {
  initial: RGB[];
  layout: ZoneGroup[];
  crossfade?: boolean;
  savedGradients: GradientPreset[];
  closeModal?: () => void;
  onApply: (stops: RGB[]) => void;
  onSave: (name: string, stops: RGB[]) => void;
  onDelete: (name: string) => void;
}

type Tab = "presets" | "tune" | "save";

const TAB_ORDER: Tab[] = ["presets", "tune", "save"];

const LAYOUT_NAME_KEYS: Record<string, string> = {
  "Left stick": "layout.leftStick",
  "Right stick": "layout.rightStick",
  Lights: "layout.lights",
};

const ACCENT = "#7c5cff";
const PANEL_BG = "rgba(18, 18, 24, 0.6)";
const BORDER = "rgba(255,255,255,0.08)";

const ACCENT_BTN: React.CSSProperties = {
  flex: 1,
  background: `linear-gradient(135deg, ${ACCENT}, #00c4ff)`,
  border: "none",
};
const SUBTLE_BTN: React.CSSProperties = {
  flex: 1,
  background: "rgba(255,255,255,0.06)",
  border: `1px solid ${BORDER}`,
};

const glowFor = (stops: RGB[]): string => {
  const a = stops[0];
  const b = stops[stops.length - 1];
  return `0 3px 16px -10px ${rgbToCss(a)}, 0 3px 16px -10px ${rgbToCss(b)}`;
};

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

const PresetTile: FC<{
  label: string;
  stops: RGB[];
  onSelect: () => void;
  onDelete?: () => void;
  deleteLabel?: string;
}> = ({ label, stops, onSelect, onDelete, deleteLabel }) => (
  <Focusable
    onActivate={onSelect}
    onClick={onSelect}
    style={{ display: "flex", flexDirection: "column", gap: 6, cursor: "pointer", position: "relative" }}
  >
    <div
      style={{
        height: 40,
        borderRadius: 12,
        background: gradientCss(stops),
        boxShadow: "inset 0 0 0 1px rgba(255,255,255,0.12)",
      }}
    />
    {onDelete && (
      <DialogButton
        onClick={(e: MouseEvent) => {
          e.stopPropagation();
          onDelete();
        }}
        aria-label={deleteLabel}
        style={{
          position: "absolute",
          top: 4,
          right: 4,
          width: 20,
          minWidth: 20,
          height: 20,
          padding: 0,
          lineHeight: "18px",
          textAlign: "center",
          fontSize: 13,
          borderRadius: 10,
          background: "rgba(0,0,0,0.55)",
          border: "1px solid rgba(255,255,255,0.18)",
        }}
      >
        ×
      </DialogButton>
    )}
    <div
      style={{
        fontSize: 11,
        fontWeight: 600,
        color: "rgba(255,255,255,0.7)",
        textAlign: "center",
      }}
    >
      {label}
    </div>
  </Focusable>
);

const StopEditor: FC<{
  label: string;
  color: RGB;
  onChange: (color: RGB) => void;
}> = ({ label, color, onChange }) => {
  const { t } = useI18n();
  const hsv = rgbToHsv(color);
  const setHue = (h: number) => onChange(hsvToRgb(h, hsv.s, Math.max(hsv.v, 60)));
  const setSat = (s: number) => onChange(hsvToRgb(hsv.h, s, Math.max(hsv.v, 60)));

  return (
    <Focusable
      style={{
        display: "flex",
        flexDirection: "column",
        gap: 2,
        padding: "10px 18px 12px",
        borderRadius: 14,
        background: "rgba(255,255,255,0.03)",
        border: `1px solid ${BORDER}`,
      }}
    >
      <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 2 }}>
        <div
          style={{
            width: 26,
            height: 26,
            borderRadius: 8,
            flex: "0 0 auto",
            background: rgbToCss(color),
            boxShadow: `0 0 10px ${rgbToCss(color)}, inset 0 0 0 1px rgba(255,255,255,0.18)`,
          }}
        />
        <div style={{ fontSize: 12.5, fontWeight: 600, color: "rgba(255,255,255,0.85)" }}>{label}</div>
      </div>
      <SliderField label={t("color.hue")} value={hsv.h} min={0} max={360} step={1} onChange={setHue} />
      <SliderField label={t("color.saturation")} value={hsv.s} min={0} max={100} step={1} onChange={setSat} />
    </Focusable>
  );
};

export const GradientModal: FC<GradientModalProps> = ({
  initial,
  layout,
  crossfade,
  savedGradients,
  closeModal,
  onApply,
  onSave,
  onDelete,
}) => {
  const { t, lang } = useI18n();
  const groups =
    layout.length > 0 ? layout : [{ name: t("gradient.defaultGroup"), region: [], zones: [0, 1] }];
  const totalZones = Math.max(2, groups.reduce((n, g) => n + g.zones.length, 0));
  const isBar = groups.length === 1 && groups[0].kind === "bar";
  const BAR_STOPS = 5;
  const count = isBar ? Math.min(BAR_STOPS, totalZones) : totalZones;
  const [stops, setStops] = useState<RGB[]>(() => expandGradient(initial, count));
  const [name, setName] = useState<string>(() => suggestGradientName(lang));
  const [tab, setTab] = useState<Tab>("presets");
  const [localSaved, setLocalSaved] = useState<GradientPreset[]>(savedGradients);
  const presetName = (preset: GradientPreset) =>
    t(`gradient.preset.${preset.name.toLowerCase()}`);
  const tabLabels: Record<Tab, string> = {
    presets: t("gradient.tab.presets"),
    tune: t("gradient.tab.tune"),
    save: t("gradient.tab.save"),
  };

  const crossfadeLabel = (i: number, total: number) =>
    i === 0
      ? t("gradient.colorStart")
      : i === total - 1
        ? t("gradient.colorEnd")
        : t("gradient.colorN", { n: i + 1 });
  const groupName = (name: string) =>
    LAYOUT_NAME_KEYS[name] ? t(LAYOUT_NAME_KEYS[name]) : name;
  const cells =
    isBar || crossfade
      ? Array.from({ length: count }, (_, i) => ({ zone: i, label: crossfadeLabel(i, count) }))
      : groups.flatMap((group) =>
          group.zones.map((zone, i) => ({
            zone,
            label: group.zones.length > 1 ? `${groupName(group.name)} · ${i + 1}` : groupName(group.name),
          })),
        );
  const tuneColumns = 1;

  const setStopAt = (index: number, color: RGB) =>
    setStops((prev) => prev.map((c, i) => (i === index ? color : c)));

  const apply = () => {
    onApply(stops);
    closeModal?.();
  };

  const save = () => {
    const trimmed = name.trim();
    if (!trimmed) return;
    const saved = { name: trimmed, stops: [...stops] };
    onSave(trimmed, saved.stops);
    setLocalSaved((prev) => [...prev.filter((p) => p.name !== trimmed), saved]);
    setName(suggestGradientName(lang));
    setTab("presets");
  };

  const remove = (target: string) => {
    onDelete(target);
    setLocalSaved((prev) => prev.filter((p) => p.name !== target));
  };

  return (
    <ModalRoot closeModal={closeModal} onCancel={closeModal} onOK={apply}>
      <Focusable style={{ display: "flex", flexDirection: "column", gap: 14, padding: 4 }}>
        <div>
          <div style={{ fontSize: 18, fontWeight: 800, color: "#fff" }}>{t("gradient.title")}</div>
          <div style={{ fontSize: 12, color: "rgba(255,255,255,0.5)", marginTop: 2 }}>
            {t("gradient.zonesCaption", { n: totalZones })}
          </div>
        </div>

        <div
          style={{
            height: 72,
            borderRadius: 18,
            background: gradientCss(stops),
            boxShadow: `${glowFor(stops)}, inset 0 0 0 1px rgba(255,255,255,0.12)`,
            transition: "background 160ms ease, box-shadow 200ms ease",
          }}
        />

        <Tabs value={tab} tabs={TAB_ORDER} onChange={setTab} label={(x) => tabLabels[x]} />

        {tab === "presets" && (
          <Card>
            <Focusable
              style={{
                display: "grid",
                gridTemplateColumns: "repeat(2, 1fr)",
                gap: 10,
                maxHeight: 232,
                overflowY: "auto",
                paddingRight: 2,
              }}
            >
              {GRADIENT_PRESETS.map((preset: GradientPreset, i: number) => (
                <PresetTile
                  key={`builtin-${i}`}
                  label={presetName(preset)}
                  stops={preset.stops}
                  onSelect={() => setStops(expandGradient(preset.stops, count))}
                />
              ))}
              {localSaved.map((preset) => (
                <PresetTile
                  key={`saved-${preset.name}`}
                  label={preset.name}
                  stops={preset.stops}
                  onSelect={() => setStops(expandGradient(preset.stops, count))}
                  onDelete={() => remove(preset.name)}
                  deleteLabel={t("saved.delete")}
                />
              ))}
            </Focusable>
            <Focusable style={{ display: "flex", gap: 10, marginTop: 14 }}>
              <DialogButton
                onClick={() => setStops(expandGradient(randomGradient(), count))}
                style={ACCENT_BTN}
              >
                {t("gradient.surprise")}
              </DialogButton>
              <DialogButton
                onClick={() => setStops(expandGradient(harmoniousGradient(stops[0]), count))}
                style={SUBTLE_BTN}
              >
                {t("gradient.autoPalette")}
              </DialogButton>
            </Focusable>
          </Card>
        )}

        {tab === "tune" && (
          <Card>
            <Focusable
              style={{
                display: "grid",
                gridTemplateColumns: `repeat(${tuneColumns}, minmax(0, 1fr))`,
                gap: 10,
              }}
            >
              {cells.map((cell) => (
                <StopEditor
                  key={cell.zone}
                  label={cell.label}
                  color={stops[cell.zone] ?? { r: 255, g: 255, b: 255 }}
                  onChange={(c) => setStopAt(cell.zone, c)}
                />
              ))}
            </Focusable>
          </Card>
        )}

        {tab === "save" && (
          <Card>
            <Focusable style={{ display: "flex", flexDirection: "column", gap: 10 }}>
              <TextField
                value={name}
                label={t("saved.namePlaceholder")}
                onChange={(e) => setName(e.target.value)}
              />
              <Focusable style={{ display: "flex", gap: 10 }}>
                <DialogButton onClick={() => setName(suggestGradientName(lang))} style={SUBTLE_BTN}>
                  {t("saved.suggest")}
                </DialogButton>
                <DialogButton onClick={save} disabled={!name.trim()} style={ACCENT_BTN}>
                  {t("saved.confirm")}
                </DialogButton>
              </Focusable>
            </Focusable>
          </Card>
        )}

        {tab !== "save" && (
          <Focusable style={{ display: "flex", gap: 10 }}>
            <DialogButton onClick={apply} style={ACCENT_BTN}>
              {t("gradient.apply")}
            </DialogButton>
            <DialogButton onClick={() => closeModal?.()} style={SUBTLE_BTN}>
              {t("gradient.cancel")}
            </DialogButton>
          </Focusable>
        )}
      </Focusable>
    </ModalRoot>
  );
};
