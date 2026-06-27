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

const Ring: FC<{ cx: number; gradId: string; intensity: number; lit: RGB[] }> = ({
  cx,
  gradId,
  intensity,
  lit,
}) => {
  const mid = lit[Math.floor(lit.length / 2)] ?? lit[0];
  return (
    <g>
      <circle cx={cx} cy={70} r={38} fill="none" stroke="#0c0c10" strokeWidth={14} />
      <circle
        cx={cx}
        cy={70}
        r={38}
        fill="none"
        stroke={`url(#${gradId})`}
        strokeWidth={9}
        strokeLinecap="round"
        style={{
          filter: `drop-shadow(0 0 ${4 + intensity * 9}px ${rgbToCss(mid)})`,
          opacity: 0.4 + intensity * 0.6,
          transition: "opacity 140ms ease, filter 140ms ease",
        }}
      />
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

  const makeStops = (cols: RGB[]) =>
    cols.map((c, i) => (
      <stop
        key={i}
        offset={`${cols.length === 1 ? 50 : (i / (cols.length - 1)) * 100}%`}
        stopColor={rgbToCss(c)}
      />
    ));

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
        <defs>
          <linearGradient id="ring-left" x1="0%" y1="0%" x2="100%" y2="100%">
            {makeStops(leftColors)}
          </linearGradient>
          <linearGradient id="ring-right" x1="100%" y1="0%" x2="0%" y2="100%">
            {makeStops(rightColors)}
          </linearGradient>
        </defs>
        <Ring cx={92} gradId="ring-left" intensity={intensity} lit={leftColors} />
        <Ring cx={208} gradId="ring-right" intensity={intensity} lit={rightColors} />
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
