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
const BASE_ANGLE = 70;

function relativeAngles(n: number): number[] {
  if (n === 2) return [0, 190];
  return Array.from({ length: n }, (_, i) => (i * 360) / n);
}

function conic(colors: RGB[]): string {
  if (colors.length <= 1) return rgbToCss(colors[0] ?? OFF);
  const angles = relativeAngles(colors.length);
  const stops = colors.map((c, i) => `${rgbToCss(c)} ${Math.round(angles[i])}deg`);
  stops.push(`${rgbToCss(colors[0])} 360deg`);
  return `conic-gradient(from ${BASE_ANGLE}deg, ${stops.join(", ")})`;
}

function average(colors: RGB[]): RGB {
  const n = colors.length || 1;
  return colors.reduce(
    (acc, c) => ({ r: acc.r + c.r / n, g: acc.g + c.g / n, b: acc.b + c.b / n }),
    { r: 0, g: 0, b: 0 },
  );
}

const RING_MASK =
  "radial-gradient(closest-side, rgba(0,0,0,0) 70%, #000 74%, #000 100%)";

const Ring: FC<{ colors: RGB[]; intensity: number; mirror?: boolean }> = ({
  colors,
  intensity,
  mirror,
}) => {
  const glow = rgbToCss(average(colors));
  return (
    <div
      style={{ position: "relative", width: 92, height: 92, transform: mirror ? "scaleX(-1)" : undefined }}
    >
      <div
        style={{
          position: "absolute",
          inset: 0,
          borderRadius: "50%",
          background: "#0c0c10",
          WebkitMask: RING_MASK,
          mask: RING_MASK,
        }}
      />
      <div
        style={{
          position: "absolute",
          inset: 0,
          borderRadius: "50%",
          background: conic(colors),
          WebkitMask: RING_MASK,
          mask: RING_MASK,
          filter: `drop-shadow(0 0 ${5 + intensity * 11}px ${glow}) blur(0.5px)`,
          opacity: 0.5 + intensity * 0.5,
          transition: "opacity 140ms ease, filter 140ms ease",
        }}
      />
    </div>
  );
};

export const DevicePreview: FC<DevicePreviewProps> = ({ colors, brightness, power, label }) => {
  const source = power && colors.length ? colors : [OFF];
  const lit = source.map((c) =>
    dim(softenForDisplay(c, 0.05), power ? Math.max(brightness, 18) : 100),
  );
  const half = lit.length > 1 ? Math.ceil(lit.length / 2) : lit.length;
  const leftColors = lit.length > 1 ? lit.slice(0, half) : lit;
  const rightColors = lit.length > 1 ? lit.slice(half) : lit;
  const intensity = power ? brightness / 100 : 0;

  return (
    <div
      style={{
        borderRadius: 14,
        padding: "18px 8px 12px",
        background:
          "radial-gradient(120% 90% at 50% 0%, rgba(255,255,255,0.05), rgba(0,0,0,0) 60%), #060608",
        boxShadow: "inset 0 0 0 1px rgba(255,255,255,0.05)",
      }}
    >
      <div style={{ display: "flex", justifyContent: "center", gap: 34 }}>
        <Ring colors={leftColors} intensity={intensity} mirror />
        <Ring colors={rightColors} intensity={intensity} />
      </div>
      <div
        style={{
          textAlign: "center",
          fontSize: 11,
          letterSpacing: "0.14em",
          textTransform: "uppercase",
          color: "rgba(255,255,255,0.4)",
          marginTop: 8,
        }}
      >
        {power ? (label ?? "Joystick rings") : "Off"}
      </div>
    </div>
  );
};
