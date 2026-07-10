import { ReactNode } from "react";
import { LuCircle, LuBlend, LuSparkles, LuGauge, LuTv, LuSettings } from "react-icons/lu";

export interface TabMeta {
  id: string;
  labelKey: string;
  icon: ReactNode;
}

export const PINNED_TAB = "settings";

// Container tab: its id is not a backend mode; battery/temperature map onto it.
export const SENSOR_TAB = "sensors";
export const SENSOR_MODES = ["battery", "temperature"] as const;

export function tabForMode(mode: string): string {
  return (SENSOR_MODES as readonly string[]).includes(mode) ? SENSOR_TAB : mode;
}

const ICON = 15;

export const TAB_META: TabMeta[] = [
  { id: "solid", labelKey: "mode.solid", icon: <LuCircle size={ICON} /> },
  { id: "gradient", labelKey: "mode.gradient", icon: <LuBlend size={ICON} /> },
  { id: "effect", labelKey: "mode.effect", icon: <LuSparkles size={ICON} /> },
  { id: SENSOR_TAB, labelKey: "nav.sensors", icon: <LuGauge size={ICON} /> },
  { id: "ambient", labelKey: "mode.ambient", icon: <LuTv size={ICON} /> },
  { id: PINNED_TAB, labelKey: "nav.settings", icon: <LuSettings size={ICON} /> },
];

const BY_ID = new Map(TAB_META.map((m) => [m.id, m]));

export function tabMeta(id: string): TabMeta | undefined {
  return BY_ID.get(id);
}
