import { ReactNode } from "react";
import { LuCircle, LuBlend, LuSparkles, LuBatteryFull, LuTv, LuSettings } from "react-icons/lu";

export interface TabMeta {
  id: string;
  labelKey: string;
  icon: ReactNode;
}

export const PINNED_TAB = "settings";

const ICON = 15;

export const TAB_META: TabMeta[] = [
  { id: "solid", labelKey: "mode.solid", icon: <LuCircle size={ICON} /> },
  { id: "gradient", labelKey: "mode.gradient", icon: <LuBlend size={ICON} /> },
  { id: "effect", labelKey: "mode.effect", icon: <LuSparkles size={ICON} /> },
  { id: "battery", labelKey: "mode.battery", icon: <LuBatteryFull size={ICON} /> },
  { id: "ambient", labelKey: "mode.ambient", icon: <LuTv size={ICON} /> },
  { id: PINNED_TAB, labelKey: "nav.settings", icon: <LuSettings size={ICON} /> },
];

const BY_ID = new Map(TAB_META.map((m) => [m.id, m]));

export function tabMeta(id: string): TabMeta | undefined {
  return BY_ID.get(id);
}
