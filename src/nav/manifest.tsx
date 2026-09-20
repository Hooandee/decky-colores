import { ReactNode } from "react";
import { LuCircle, LuBlend, LuSparkles, LuGauge, LuTv, LuSettings, LuClock, LuAudioLines } from "react-icons/lu";

export interface TabMeta {
  id: string;
  labelKey: string;
  icon: ReactNode;
  accent: string;
}

export const PINNED_TAB = "settings";

export function isProfileScopeVisible(tabId: string): boolean {
  return tabId !== PINNED_TAB;
}

// Container tab: its id is not a backend mode; battery/temperature map onto it.
export const SENSOR_TAB = "sensors";
export const SENSOR_MODES = ["battery", "temperature", "performance"] as const;

export function tabForMode(mode: string): string {
  return (SENSOR_MODES as readonly string[]).includes(mode) ? SENSOR_TAB : mode;
}

const ICON = 15;

export const TAB_META: TabMeta[] = [
  { id: "solid", labelKey: "mode.solid", icon: <LuCircle size={ICON} />, accent: "#5b8cff" },
  {
    id: "gradient",
    labelKey: "mode.gradient",
    icon: <LuBlend size={ICON} />,
    accent: "linear-gradient(135deg, #22c7c0, #9b7bf0)",
  },
  { id: "effect", labelKey: "mode.effect", icon: <LuSparkles size={ICON} />, accent: "#ec5c9d" },
  { id: SENSOR_TAB, labelKey: "nav.sensors", icon: <LuGauge size={ICON} />, accent: "#3fbf6f" },
  { id: "clock", labelKey: "mode.clock", icon: <LuClock size={ICON} />, accent: "#e0952a" },
  { id: "vu", labelKey: "mode.vu", icon: <LuAudioLines size={ICON} />, accent: "#22c7c0" },
  { id: "ambient", labelKey: "mode.ambient", icon: <LuTv size={ICON} />, accent: "#5b8cff" },
  { id: PINNED_TAB, labelKey: "nav.settings", icon: <LuSettings size={ICON} />, accent: "#8b92a3" },
];

const BY_ID = new Map(TAB_META.map((m) => [m.id, m]));

export function tabMeta(id: string): TabMeta | undefined {
  return BY_ID.get(id);
}
