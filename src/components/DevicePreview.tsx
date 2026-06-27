import { FC } from "react";
import { RGB } from "../types";
import { dim, rgbToCss, softenForDisplay } from "../color";

interface DevicePreviewProps {
  colors: RGB[];
  brightness: number;
  power: boolean;
  label?: string;
}

const OFF: RGB = { r: 26, g: 26, b: 32 };
const R = 38;
const CIRC = 2 * Math.PI * R;
const GAP = 7;

const Ring: FC<{ cx: number; intensity: number; lit: RGB[] }> = ({ cx, intensity, lit }) => {
  const n = Math.max(1, lit.length);
  const seg = CIRC / n;
  const arc = Math.max(2, seg - (n > 1 ? GAP : 0));
  return (
    <g transform={`rotate(-90 ${cx} 70)`}>
      <circle cx={cx} cy={70} r={R} fill="none" stroke="#0c0c10" strokeWidth={14} />
      {lit.map((c, i) => (
        <circle
          key={i}
          cx={cx}
          cy={70}
          r={R}
          fill="none"
          stroke={rgbToCss(c)}
          strokeWidth={9}
          strokeLinecap="butt"
          strokeDasharray={`${arc} ${CIRC - arc}`}
          strokeDashoffset={-i * seg}
          style={{
            filter: `drop-shadow(0 0 ${4 + intensity * 8}px ${rgbToCss(c)})`,
            opacity: 0.4 + intensity * 0.6,
            transition: "stroke 140ms ease, opacity 140ms ease, filter 140ms ease",
          }}
        />
      ))}
    </g>
  );
};

export const DevicePreview: FC<DevicePreviewProps> = ({ colors, brightness, power, label }) => {
  const source = power && colors.length ? colors : [OFF];
  const lit = source.map((c) => dim(softenForDisplay(c), power ? Math.max(brightness, 12) : 100));
  const intensity = power ? brightness / 100 : 0;

  const half = lit.length > 1 ? Math.ceil(lit.length / 2) : lit.length;
  const leftColors = lit.length > 1 ? lit.slice(0, half) : lit;
  const rightColors = lit.length > 1 ? lit.slice(half) : lit;

  return (
    <div
      style={{
        position: "relative",
        borderRadius: 14,
        padding: "16px 8px 10px",
        background:
          "radial-gradient(120% 90% at 50% 0%, rgba(255,255,255,0.05), rgba(0,0,0,0) 60%), #060608",
        boxShadow: "inset 0 0 0 1px rgba(255,255,255,0.05)",
        overflow: "hidden",
      }}
    >
      <svg viewBox="0 0 300 140" style={{ width: "100%", display: "block" }}>
        <Ring cx={92} intensity={intensity} lit={leftColors} />
        <Ring cx={208} intensity={intensity} lit={rightColors} />
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
        {power ? (label ?? "Joystick rings") : "Off"}
      </div>
    </div>
  );
};
