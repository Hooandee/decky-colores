import { FC, useMemo, useState } from "react";
import {
  ModalRoot,
  Focusable,
  SliderField,
  ButtonItem,
  DialogButton,
} from "@decky/ui";
import { RGB, GradientPreset } from "../types";
import { hsvToRgb, rgbToHsv, rgbToCss } from "../color";
import { GRADIENT_PRESETS, harmoniousGradient, randomGradient } from "../palette";

interface GradientModalProps {
  initial: RGB[];
  closeModal?: () => void;
  onApply: (stops: RGB[]) => void;
}

const ACCENT = "#7c5cff";
const PANEL_BG = "rgba(18, 18, 24, 0.6)";
const BORDER = "rgba(255,255,255,0.08)";

const normalizeStops = (stops: RGB[]): RGB[] => {
  const safe = stops && stops.length >= 2 ? stops : null;
  if (safe) return safe.slice(0, 4);
  return [
    { r: 124, g: 92, b: 255 },
    { r: 0, g: 196, b: 255 },
  ];
};

const cssGradient = (stops: RGB[], angle = 90): string => {
  if (stops.length === 1) return rgbToCss(stops[0]);
  const segments = stops.map(rgbToCss).join(", ");
  return `linear-gradient(${angle}deg, ${segments})`;
};

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

const Card: FC<{ children: React.ReactNode; style?: React.CSSProperties }> = ({
  children,
  style,
}) => (
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

interface StopEditorProps {
  label: string;
  color: RGB;
  onChange: (color: RGB) => void;
}

const StopEditor: FC<StopEditorProps> = ({ label, color, onChange }) => {
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
            width: 38,
            height: 38,
            borderRadius: 10,
            flex: "0 0 auto",
            background: rgbToCss(color),
            boxShadow: `0 0 14px ${rgbToCss(color)}, inset 0 0 0 1px rgba(255,255,255,0.18)`,
          }}
        />
        <div
          style={{
            fontSize: 13,
            fontWeight: 600,
            color: "rgba(255,255,255,0.85)",
          }}
        >
          {label}
        </div>
      </div>
      <SliderField
        label="Hue"
        value={hsv.h}
        min={0}
        max={360}
        step={1}
        onChange={setHue}
      />
      <SliderField
        label="Saturation"
        value={hsv.s}
        min={0}
        max={100}
        step={1}
        onChange={setSat}
      />
    </Focusable>
  );
};

export const GradientModal: FC<GradientModalProps> = ({
  initial,
  closeModal,
  onApply,
}) => {
  const [stops, setStops] = useState<RGB[]>(() => normalizeStops(initial));

  const start = stops[0];
  const end = stops[stops.length - 1];

  const previewGradient = useMemo(() => cssGradient(stops), [stops]);
  const glow = useMemo(() => glowFor(stops), [stops]);

  const setStart = (color: RGB) =>
    setStops((prev) => {
      const next = [...prev];
      next[0] = color;
      return next;
    });

  const setEnd = (color: RGB) =>
    setStops((prev) => {
      const next = [...prev];
      next[next.length - 1] = color;
      return next;
    });

  const apply = () => {
    onApply(stops);
    closeModal?.();
  };

  return (
    <ModalRoot closeModal={closeModal} onCancel={closeModal} onOK={apply}>
      <Focusable
        style={{
          display: "flex",
          flexDirection: "column",
          gap: 20,
          padding: 4,
        }}
      >
        <div>
          <div
            style={{
              fontSize: 18,
              fontWeight: 800,
              color: "#fff",
              letterSpacing: "0.01em",
            }}
          >
            Crear degradado
          </div>
          <div
            style={{
              fontSize: 12,
              color: "rgba(255,255,255,0.5)",
              marginTop: 2,
            }}
          >
            Elige un preset o ajusta los colores. Te ayudamos con el resto.
          </div>
        </div>

        <div
          style={{
            height: 84,
            borderRadius: 18,
            background: previewGradient,
            boxShadow: `${glow}, inset 0 0 0 1px rgba(255,255,255,0.12)`,
            transition: "background 160ms ease, box-shadow 200ms ease",
          }}
        />

        <Card>
          <SectionLabel>Presets</SectionLabel>
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
                onActivate={() => setStops(normalizeStops(preset.stops))}
                onClick={() => setStops(normalizeStops(preset.stops))}
                style={{
                  display: "flex",
                  flexDirection: "column",
                  gap: 6,
                  cursor: "pointer",
                }}
              >
                <div
                  style={{
                    height: 40,
                    borderRadius: 12,
                    background: cssGradient(preset.stops),
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
                  {preset.name}
                </div>
              </Focusable>
            ))}
          </Focusable>
        </Card>

        <Card>
          <SectionLabel>Constructor</SectionLabel>
          <Focusable
            style={{
              display: "flex",
              flexDirection: "column",
              gap: 12,
            }}
          >
            <StopEditor label="Color inicial" color={start} onChange={setStart} />
            <StopEditor label="Color final" color={end} onChange={setEnd} />
          </Focusable>
        </Card>

        <Card>
          <SectionLabel>Asistente</SectionLabel>
          <Focusable
            style={{
              display: "flex",
              gap: 10,
            }}
          >
            <DialogButton
              onClick={() => setStops(normalizeStops(randomGradient()))}
              style={{
                flex: 1,
                background: `linear-gradient(135deg, ${ACCENT}, #00c4ff)`,
                border: "none",
              }}
            >
              Sorpréndeme
            </DialogButton>
            <DialogButton
              onClick={() => setStops(normalizeStops(harmoniousGradient(start)))}
              style={{
                flex: 1,
                background: "rgba(255,255,255,0.06)",
                border: `1px solid ${BORDER}`,
              }}
            >
              Auto-paleta
            </DialogButton>
          </Focusable>
        </Card>

        <Focusable
          style={{
            display: "flex",
            flexDirection: "column",
            gap: 4,
          }}
        >
          <ButtonItem layout="below" bottomSeparator="none" onClick={apply}>
            Aplicar
          </ButtonItem>
          <ButtonItem layout="below" bottomSeparator="none" onClick={() => closeModal?.()}>
            Cancelar
          </ButtonItem>
        </Focusable>
      </Focusable>
    </ModalRoot>
  );
};
