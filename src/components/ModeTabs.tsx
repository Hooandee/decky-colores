import { FC } from "react";
import { Mode } from "../types";
import { useI18n } from "../i18n";
import { Tabs } from "./Tabs";

interface ModeTabsProps {
  value: Mode;
  modes: Mode[];
  onChange: (m: Mode) => void;
}

const LABEL_KEYS: Record<Mode, string> = {
  solid: "mode.solid",
  gradient: "mode.gradient",
  effect: "mode.effect",
  ambient: "mode.ambient",
};

export const ModeTabs: FC<ModeTabsProps> = ({ value, modes, onChange }) => {
  const { t } = useI18n();
  return <Tabs value={value} tabs={modes} onChange={onChange} label={(m) => t(LABEL_KEYS[m])} />;
};
