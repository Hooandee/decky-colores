import { FC } from "react";
import { RGB } from "../types";
import { dim, rgbToCss } from "../color";

interface DevicePreviewProps {
  color: RGB;
  brightness: number;
  power: boolean;
}

const Ring: FC<{ cx: number; glow: string; intensity: number }> = ({ cx, glow, intensity }) => (
  <g>
    <circle
      cx={cx}
      cy={70}
      r={38}
      fill="none"
      stroke="#0c0c10"
      strokeWidth={14}
    />
    <circle
      cx={cx}
      cy={70}
      r={38}
      fill="none"
      stroke={glow}
      strokeWidth={9}
      strokeLinecap="round"
      style={{
        filter: `drop-shadow(0 0 ${6 + intensity * 14}px ${glow})`,
        opacity: 0.35 + intensity * 0.65,
        transition: "stroke 120ms ease, opacity 120ms ease, filter 120ms ease",
      }}
    />
  </g>
);

export const DevicePreview: FC<DevicePreviewProps> = ({ color, brightness, power }) => {
  const lit = power ? dim(color, brightness) : { r: 24, g: 24, b: 30 };
  const css = rgbToCss(lit);
  const intensity = power ? brightness / 100 : 0;

  return (
    <div
      style={{
        position: "relative",
        borderRadius: 12,
        padding: "14px 8px 10px",
        background:
          "radial-gradient(120% 90% at 50% 0%, rgba(255,255,255,0.05), rgba(0,0,0,0) 60%), #060608",
        boxShadow: "inset 0 0 0 1px rgba(255,255,255,0.05)",
        overflow: "hidden",
      }}
    >
      <svg viewBox="0 0 300 140" style={{ width: "100%", display: "block" }}>
        <Ring cx={92} glow={css} intensity={intensity} />
        <Ring cx={208} glow={css} intensity={intensity} />
      </svg>
      <div
        style={{
          textAlign: "center",
          fontSize: 11,
          letterSpacing: "0.14em",
          textTransform: "uppercase",
          color: "rgba(255,255,255,0.4)",
          marginTop: 2,
        }}
      >
        {power ? "Joystick rings" : "Off"}
      </div>
    </div>
  );
};
