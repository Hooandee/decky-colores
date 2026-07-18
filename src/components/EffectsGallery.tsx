import { FC } from "react";
import { Focusable, SliderField } from "@decky/ui";
import { EffectId, EffectMeta, RGB } from "../types";
import { rgbToCss } from "../color";
import { useI18n } from "../i18n";

interface EffectsGalleryProps {
  effects: EffectMeta[];
  selected: EffectId;
  speed: number;
  disabled?: boolean;
  firmwareSpiral?: boolean;
  onSelect: (id: EffectId) => void;
  onSpeed: (v: number) => void;
}

const PREFIX = "colores-fx-";

const Keyframes: FC = () => (
  <style>{`
    @keyframes ${PREFIX}breathe {
      0%, 100% { opacity: 0.35; transform: scale(0.78); }
      50% { opacity: 1; transform: scale(1); }
    }
    @keyframes ${PREFIX}hue {
      from { filter: hue-rotate(0deg); }
      to { filter: hue-rotate(360deg); }
    }
    @keyframes ${PREFIX}travel {
      from { background-position: 0% 50%; }
      to { background-position: 200% 50%; }
    }
    @keyframes ${PREFIX}spin {
      from { transform: rotate(0deg); }
      to { transform: rotate(360deg); }
    }
    @keyframes ${PREFIX}sweep {
      0% { left: 0%; }
      50% { left: 100%; }
      100% { left: 0%; }
    }
    @keyframes ${PREFIX}twinkle {
      0%, 100% { opacity: 0.15; transform: scale(0.7); }
      50% { opacity: 1; transform: scale(1); }
    }
  `}</style>
);

const stops = (colors: RGB[]): string => {
  const list = colors.length ? colors : [{ r: 120, g: 120, b: 120 }];
  return list.map(rgbToCss).join(", ");
};

const GRADIENT_ANIM: Partial<Record<EffectId, string>> = {
  wave: `${PREFIX}travel 3s linear infinite`,
  ripple: `${PREFIX}travel 4s ease-in-out infinite`,
  aurora: `${PREFIX}travel 5s linear infinite, ${PREFIX}hue 7s linear infinite`,
};

const Thumb: FC<{ effect: EffectMeta }> = ({ effect }) => {
  const base: React.CSSProperties = {
    width: "100%",
    height: 38,
    borderRadius: 8,
    overflow: "hidden",
  };

  if (effect.id === "breathing") {
    const c = effect.colors[0] ?? { r: 255, g: 255, b: 255 };
    return (
      <div
        style={{
          ...base,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          background: "rgba(0,0,0,0.25)",
        }}
      >
        <div
          style={{
            width: 22,
            height: 22,
            borderRadius: "50%",
            background: rgbToCss(c),
            boxShadow: `0 0 14px ${rgbToCss(c)}`,
            animation: `${PREFIX}breathe 2.4s ease-in-out infinite`,
          }}
        />
      </div>
    );
  }

  if (effect.id === "rainbow") {
    return (
      <div
        style={{
          ...base,
          background:
            "linear-gradient(90deg, #ff0040, #ffae00, #28e070, #00c4ff, #8a5cff, #ff0040)",
          animation: `${PREFIX}hue 3s linear infinite`,
        }}
      />
    );
  }

  const gradientAnim = GRADIENT_ANIM[effect.id];
  if (gradientAnim) {
    return (
      <div
        style={{
          ...base,
          backgroundImage: `linear-gradient(90deg, ${stops(effect.colors)}, ${
            effect.colors.length ? rgbToCss(effect.colors[0]) : "#888"
          })`,
          backgroundSize: "200% 100%",
          animation: gradientAnim,
        }}
      />
    );
  }

  if (effect.id === "comet") {
    const c = effect.colors[0] ?? { r: 255, g: 255, b: 255 };
    return (
      <div style={{ ...base, position: "relative", background: "rgba(0,0,0,0.3)" }}>
        <div
          style={{
            position: "absolute",
            top: "50%",
            width: 14,
            height: 14,
            marginTop: -7,
            marginLeft: -7,
            borderRadius: "50%",
            background: rgbToCss(c),
            boxShadow: `0 0 12px ${rgbToCss(c)}`,
            animation: `${PREFIX}sweep 2.4s ease-in-out infinite`,
          }}
        />
      </div>
    );
  }

  if (effect.id === "sparkle") {
    const c = effect.colors[0] ?? { r: 255, g: 255, b: 255 };
    const dots = [8, 24, 40, 56, 72, 88];
    return (
      <div style={{ ...base, position: "relative", background: "rgba(0,0,0,0.3)" }}>
        {dots.map((x, i) => (
          <div
            key={i}
            style={{
              position: "absolute",
              top: i % 2 ? "32%" : "62%",
              left: `${x}%`,
              width: 5,
              height: 5,
              borderRadius: "50%",
              background: rgbToCss(c),
              boxShadow: `0 0 6px ${rgbToCss(c)}`,
              animation: `${PREFIX}twinkle ${1.2 + 0.25 * i}s ease-in-out ${0.18 * i}s infinite`,
            }}
          />
        ))}
      </div>
    );
  }

  return (
    <div
      style={{
        ...base,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        background: "rgba(0,0,0,0.25)",
      }}
    >
      <div
        style={{
          width: 30,
          height: 30,
          borderRadius: "50%",
          background: `conic-gradient(${stops(effect.colors)}, ${
            effect.colors.length ? rgbToCss(effect.colors[0]) : "#888"
          })`,
          animation: `${PREFIX}spin 4s linear infinite`,
        }}
      />
    </div>
  );
};

export const EffectsGallery: FC<EffectsGalleryProps> = ({
  effects,
  selected,
  speed,
  disabled,
  firmwareSpiral,
  onSelect,
  onSpeed,
}) => {
  const { t } = useI18n();
  const labelFor = (id: EffectId) =>
    id === "spiral" && firmwareSpiral
      ? t("effect.spiral.legion.label")
      : t(`effect.${id}.label`);
  return (
  <div style={{ display: "flex", flexDirection: "column", gap: 12, opacity: disabled ? 0.4 : 1 }}>
    <Keyframes />
    <Focusable
      style={{
        display: "grid",
        gridTemplateColumns: "repeat(2, 1fr)",
        gap: 8,
      }}
    >
      {effects.map((effect) => {
        const active = effect.id === selected;
        const glow = effect.colors[0]
          ? rgbToCss(effect.colors[0])
          : "#5b8cff";
        return (
          <Focusable
            key={effect.id}
            onActivate={() => !disabled && onSelect(effect.id)}
            onClick={() => !disabled && onSelect(effect.id)}
            style={{
              display: "flex",
              flexDirection: "column",
              gap: 6,
              padding: 8,
              borderRadius: 12,
              background: "rgba(255,255,255,0.03)",
              border: active
                ? `1px solid ${glow}`
                : "1px solid rgba(255,255,255,0.08)",
              boxShadow: active ? `0 0 14px ${glow}55` : "none",
              cursor: disabled ? "default" : "pointer",
              transition: "border 140ms ease, box-shadow 140ms ease",
            }}
          >
            <Thumb effect={effect} />
            <div
              style={{
                fontSize: 12,
                fontWeight: active ? 700 : 500,
                color: active ? "#fff" : "rgba(255,255,255,0.7)",
                textAlign: "center",
              }}
            >
              {labelFor(effect.id)}
            </div>
          </Focusable>
        );
      })}
    </Focusable>
    <SliderField
      label={t("effect.speed")}
      value={speed}
      min={0}
      max={100}
      step={1}
      valueSuffix="%"
      showValue
      disabled={disabled}
      onChange={onSpeed}
    />
  </div>
  );
};
